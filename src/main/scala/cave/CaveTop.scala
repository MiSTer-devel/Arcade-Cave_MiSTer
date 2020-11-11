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
      // Fast clock domain
      val rst_i = Input(Reset())
      val clk_fast_i = Input(Clock())
      // CPU clock domain
      val rst_68k_i = Input(Reset())
      val clk_68k_i = Input(Clock())
      // Player inputs
      val player_1_i = Input(Bits())
      val player_2_i = Input(Bits())
      // CPU
      val cpu = Flipped(new M68KIO)
      // Memory bus
      val mem_bus_ack = Output(Bool())
      val mem_bus_data = Output(Bits(M68K.DATA_WIDTH.W))
      // Program ROM
      val rom_addr_68k_o = Output(UInt(Config.PROG_ROM_ADDR_WIDTH.W))
      val rom_read_68k_o = Output(Bool())
      val rom_valid_68k_i = Input(Bool())
      val rom_data_68k_i = Input(Bits(Config.PROG_ROM_DATA_WIDTH.W))
      // Tile ROM
      val rom_addr_gfx_o = Output(UInt(Config.TILE_ROM_ADDR_WIDTH.W))
      val tiny_burst_gfx_o = Output(Bool())
      val rom_burst_read_gfx_o = Output(Bool())
      val rom_data_valid_gfx_i = Input(Bool())
      val rom_data_gfx_i = Input(Bits(Config.TILE_ROM_DATA_WIDTH.W))
      val rom_burst_done_gfx_i = Input(Bool())
      // Frame buffer
      val frame_buffer_addr_o = Output(UInt(Config.FRAME_BUFFER_ADDR_WIDTH.W))
      val frame_buffer_data_o = Output(Bits(Config.FRAME_BUFFER_DATA_WIDTH.W))
      val frame_buffer_write_o = Output(Bool())
      val frame_buffer_dma_start_o = Output(Bool())
      val frame_buffer_dma_done_i = Input(Bool())
      // Vertical blank
      val vblank_i = Input(Bool())
    })

    override def desiredName = "cave"
  }

  // Wires
  val mainRamAck = Wire(Bool())
  val mainRamData = Wire(Bits())

  // M68000 CPU
  //
  // The CPU runs in the CPU clock domain.
  val cpu = withClockAndReset(io.cpuClock, io.cpuReset) {
    val cpu = Module(new M68K)

    val readStrobe = Util.rising(cpu.io.as) && cpu.io.rw
    val writeStrobe = Util.rising(cpu.io.as) && !cpu.io.rw
    val highWriteStrobe = Util.rising(cpu.io.uds) && !cpu.io.rw
    val lowWriteStrobe = Util.rising(cpu.io.lds) && !cpu.io.rw
    val mainRamEnable = cpu.io.addr >= 0x100000.U && cpu.io.addr <= 0x10ffff.U

    // Main RAM
    val mainRam = Module(new SinglePortRam(MAIN_RAM_ADDR_WIDTH, MAIN_RAM_DATA_WIDTH))
    mainRam.io.din := cpu.io.dout
    mainRam.io.rd := mainRamEnable && readStrobe
    mainRam.io.wr := mainRamEnable && (highWriteStrobe || lowWriteStrobe)
    mainRam.io.addr := cpu.io.addr(15, 1)
    mainRam.io.mask := cpu.io.uds ## cpu.io.lds
    mainRam.io.din := cpu.io.dout
    mainRamAck := RegNext(mainRamEnable && (readStrobe || highWriteStrobe || lowWriteStrobe))
    mainRamData := Mux(mainRamEnable, mainRam.io.dout, 0.U)

    cpu
  }

  // Cave
  val cave = Module(new CaveBlackBox)
  cave.io.rst_i := reset
  cave.io.clk_fast_i := clock
  cave.io.clk_68k_i := io.cpuClock
  cave.io.rst_68k_i := io.cpuReset

  cave.io.player_1_i := io.player.player1
  cave.io.player_2_i := io.player.player2

  cave.io.cpu <> cpu.io
  cpu.io.dtack := mainRamAck | cave.io.mem_bus_ack
  cpu.io.din := mainRamData | cave.io.mem_bus_data

  io.progRom.addr := cave.io.rom_addr_68k_o + Config.PROG_ROM_OFFSET.U
  io.progRom.rd := cave.io.rom_read_68k_o
  cave.io.rom_valid_68k_i := io.progRom.valid
  cave.io.rom_data_68k_i := io.progRom.dout

  io.tileRom.addr := cave.io.rom_addr_gfx_o + Config.TILE_ROM_OFFSET.U
  io.tileRom.tinyBurst := cave.io.tiny_burst_gfx_o
  io.tileRom.rd := cave.io.rom_burst_read_gfx_o
  cave.io.rom_data_valid_gfx_i := io.tileRom.valid
  cave.io.rom_data_gfx_i := io.tileRom.dout
  cave.io.rom_burst_done_gfx_i := io.tileRom.burstDone

  io.frameBuffer.addr := cave.io.frame_buffer_addr_o
  io.frameBuffer.mask := 0.U
  io.frameBuffer.din := cave.io.frame_buffer_data_o
  io.frameBuffer.wr := cave.io.frame_buffer_write_o
  io.frameBuffer.dmaStart := cave.io.frame_buffer_dma_start_o
  cave.io.frame_buffer_dma_done_i := io.frameBuffer.dmaDone

  cave.io.vblank_i := io.video.vBlank

  io.debug.pc := cpu.io.debug.pc
  io.debug.pcw := cpu.io.debug.pcw
}
