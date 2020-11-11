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
import axon.cpu.m68k.{CPU, CPUIO}
import axon.gpu.VideoIO
import axon.mem._
import cave.types._
import chisel3._

/** Represents the CAVE arcade hardware. */
class CaveTop extends Module {
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
      val vblank_i = Input(Bool())
      val player = new PlayerIO
      val cpu = Flipped(new CPUIO)
      val memBus = new Bundle {
        val ack = Output(Bool())
        val data = Output(Bits(CPU.DATA_WIDTH.W))
      }
      val tileRom = new TileRomIO
      val spriteRam = ReadMemIO(Config.SPRITE_RAM_GPU_ADDR_WIDTH, Config.SPRITE_RAM_GPU_DATA_WIDTH)
      val frameBuffer = new FrameBufferIO
    })

    override def desiredName = "cave"
  }

  // Wires
  val progRomAck = Wire(Bool())
  val progRomData = Wire(Bits())

  // M68000 CPU
  //
  // The CPU runs in the CPU clock domain.
  val cpu = withClockAndReset(io.cpuClock, io.cpuReset) { Module(new CPU) }
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
    Module(new SinglePortRam(
      addrWidth = Config.MAIN_RAM_ADDR_WIDTH,
      dataWidth = Config.MAIN_RAM_DATA_WIDTH
    ))
  }

  // Sprite RAM
  val spriteRam = withClockAndReset(io.cpuClock, io.cpuReset) {
    Module(new TrueDualPortRam(
      addrWidthA = Config.SPRITE_RAM_ADDR_WIDTH,
      dataWidthA = Config.SPRITE_RAM_DATA_WIDTH,
      addrWidthB = Config.SPRITE_RAM_GPU_ADDR_WIDTH,
      dataWidthB = Config.SPRITE_RAM_GPU_DATA_WIDTH
    ))
  }
  spriteRam.io.clockB := clock

  // Cave
  val cave = Module(new CaveBlackBox)
  cave.io.rst_i := reset
  cave.io.clk_i := clock
  cave.io.clk_68k_i := io.cpuClock
  cave.io.rst_68k_i := io.cpuReset

  cave.io.vblank_i := io.video.vBlank

  io.player <> cave.io.player

  cpu.io <> cave.io.cpu
  cpu.io.dtack := progRomAck | cave.io.memBus.ack
  cpu.io.din := progRomData | cave.io.memBus.data

  spriteRam.io.portB <> cave.io.spriteRam

  io.tileRom <> cave.io.tileRom
  io.tileRom.addr := cave.io.tileRom.addr + Config.TILE_ROM_OFFSET.U

  io.frameBuffer <> cave.io.frameBuffer

  io.debug.pc := cpu.io.debug.pc
  io.debug.pcw := cpu.io.debug.pcw

  // Memory map
  withClockAndReset(io.cpuClock, io.cpuReset) {
    cpu.memMap(0x100000 to 0x10ffff).ram(mainRam.io)
    cpu.memMap(0x400000 to 0x40ffff).ram(spriteRam.io.portA)
  }
}
