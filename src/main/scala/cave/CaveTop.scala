/*
 *    __   __     __  __     __         __
 *   /\ "-.\ \   /\ \/\ \   /\ \       /\ \
 *   \ \ \-.  \  \ \ \_\ \  \ \ \____  \ \ \____
 *    \ \_\\"\_\  \ \_____\  \ \_____\  \ \_____\
 *     \/_/ \/_/   \/_____/   \/_____/   \/_____/
 *    ______     ______       __     ______     ______     ______
 *   /\  __ \   /\  == \     /\ \   /\  ___\   /\  ___\   /\__  _\
 *   \ \ \/\ \  \ \  __<    _\_\ \  \ \  __\   \ \ \____  \/_/\ \/
 *    \ \_____\  \ \_____\ /\_____\  \ \_____\  \ \_____\    \ \_\
 *     \/_____/   \/_____/ \/_____/   \/_____/   \/_____/     \/_/
 *
 *  https://joshbassett.info
 *  https://twitter.com/nullobject
 *  https://github.com/nullobject
 *
 *  Copyright (c) 2020 Josh Bassett
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
 */

package cave

import axon.Util
import axon.cpu._
import axon.gpu.VideoIO
import axon.mem._
import cave.types._
import chisel3._

/** Represents the CAVE arcade hardware. */
class CaveTop extends Module {
  val MAIN_RAM_ADDR_WIDTH = 15
  val MAIN_RAM_DATA_WIDTH = 16

  val SPRITE_RAM_ADDR_WIDTH = 15
  val SPRITE_RAM_DATA_WIDTH = 16
  val SPRITE_RAM_GPU_ADDR_WIDTH = 12
  val SPRITE_RAM_GPU_DATA_WIDTH = 128

  val io = IO(new Bundle {
    /** CPU clock domain */
    val cpuClock = Input(Clock())
    /** CPU reset */
    val cpuReset = Input(Reset())
    /** Player port */
    val player = new PlayerIO
    /** Program ROM port */
    val progRom = new ProgRomIO
    /** Tile ROM port */
    val tileRom = new TileRomIO
    /** Frame buffer port */
    val frameBuffer = new FrameBufferIO
    /** Video port */
    val video = Input(new VideoIO)
    /** Debug port */
    val debug = new Bundle {
      val pc = Output(UInt())
      val pcw = Output(Bool())
    }
  })

  class CaveBlackBox extends BlackBox {
    val io = IO(new Bundle {
      val rst_i = Input(Reset())
      val clk_i = Input(Clock())
      val rst_68k_i = Input(Reset())
      val clk_68k_i = Input(Clock())
      val player = new PlayerIO
      val cpu = Flipped(new M68KIO)
      val memBus = new Bundle {
        val ack = Output(Bool())
        val data = Output(Bits(M68K.DATA_WIDTH.W))
      }
      val tileRom = new TileRomIO
      val spriteRam = ReadMemIO(SPRITE_RAM_GPU_ADDR_WIDTH, SPRITE_RAM_GPU_DATA_WIDTH)
      val frameBuffer = new FrameBufferIO
      val vblank_i = Input(Bool())
    })

    override def desiredName = "cave"
  }

  // Wires
  val progRomAck = Wire(Bool())
  val progRomData = Wire(Bits())
  val mainRamAck = Wire(Bool())
  val mainRamData = Wire(Bits())
  val spriteRamAck = Wire(Bool())
  val spriteRamData = Wire(Bits())

  // M68000 CPU
  //
  // The CPU runs in the CPU clock domain.
  val cpu = withClockAndReset(io.cpuClock, io.cpuReset) { Module(new M68K) }
  val readStrobe = withClockAndReset(io.cpuClock, io.cpuReset) { Util.rising(cpu.io.as) && cpu.io.rw }
  val writeStrobe = withClockAndReset(io.cpuClock, io.cpuReset) { Util.rising(cpu.io.as) && !cpu.io.rw }
  val highWriteStrobe = withClockAndReset(io.cpuClock, io.cpuReset) { Util.rising(cpu.io.uds) && !cpu.io.rw }
  val lowWriteStrobe = withClockAndReset(io.cpuClock, io.cpuReset) { Util.rising(cpu.io.lds) && !cpu.io.rw }

  // Program ROM
  val progRomEnable = cpu.io.addr >= 0x000000.U && cpu.io.addr <= 0x0fffff.U
  io.progRom.rd := progRomEnable && readStrobe
  io.progRom.addr := cpu.io.addr + Config.PROG_ROM_OFFSET.U
  progRomAck := io.progRom.valid
  progRomData := Mux(progRomEnable, io.progRom.dout, 0.U)

  // Main RAM
  val mainRam = withClockAndReset(io.cpuClock, io.cpuReset) {
    val mainRamEnable = cpu.io.addr >= 0x100000.U && cpu.io.addr <= 0x10ffff.U
    val mainRam = Module(new SinglePortRam(MAIN_RAM_ADDR_WIDTH, MAIN_RAM_DATA_WIDTH))
    mainRam.io.din := cpu.io.dout
    mainRam.io.rd := mainRamEnable && readStrobe
    mainRam.io.wr := mainRamEnable && (highWriteStrobe || lowWriteStrobe)
    mainRam.io.addr := cpu.io.addr(15, 1)
    mainRam.io.mask := cpu.io.uds ## cpu.io.lds
    mainRam.io.din := cpu.io.dout
    mainRamAck := RegNext(mainRamEnable && (readStrobe || highWriteStrobe || lowWriteStrobe))
    mainRamData := Mux(mainRamEnable, mainRam.io.dout, 0.U)
    mainRam
  }

  // Sprite RAM
  val spriteRam = withClockAndReset(io.cpuClock, io.cpuReset) {
    val spriteRamEnable = cpu.io.addr >= 0x400000.U && cpu.io.addr <= 0x40ffff.U
    val spriteRam = Module(new TrueDualPortRam(SPRITE_RAM_ADDR_WIDTH, SPRITE_RAM_DATA_WIDTH, SPRITE_RAM_GPU_ADDR_WIDTH, SPRITE_RAM_GPU_DATA_WIDTH))
    spriteRam.io.clockA := clock
    spriteRam.io.portA.din := cpu.io.dout
    spriteRam.io.portA.rd := spriteRamEnable && readStrobe
    spriteRam.io.portA.wr := spriteRamEnable && (highWriteStrobe || lowWriteStrobe)
    spriteRam.io.portA.addr := cpu.io.addr(15, 1)
    spriteRam.io.portA.mask := cpu.io.uds ## cpu.io.lds
    spriteRam.io.portA.din := cpu.io.dout
    spriteRamAck := RegNext(spriteRamEnable && (readStrobe || highWriteStrobe || lowWriteStrobe))
    spriteRamData := Mux(spriteRamEnable, spriteRam.io.portA.dout, 0.U)
    spriteRam
  }

  // Cave
  val cave = Module(new CaveBlackBox)
  cave.io.rst_i := reset
  cave.io.clk_i := clock
  cave.io.clk_68k_i := io.cpuClock
  cave.io.rst_68k_i := io.cpuReset

  io.player <> cave.io.player

  cpu.io <> cave.io.cpu
  cpu.io.dtack := progRomAck | mainRamAck | spriteRamAck | cave.io.memBus.ack
  cpu.io.din := progRomData | mainRamData | spriteRamData | cave.io.memBus.data

  spriteRam.io.clockB := clock
  spriteRam.io.portB <> cave.io.spriteRam

  io.tileRom <> cave.io.tileRom
  io.tileRom.addr := cave.io.tileRom.addr + Config.TILE_ROM_OFFSET.U

  io.frameBuffer <> cave.io.frameBuffer

  cave.io.vblank_i := io.video.vBlank

  io.debug.pc := cpu.io.debug.pc
  io.debug.pcw := cpu.io.debug.pcw
}
