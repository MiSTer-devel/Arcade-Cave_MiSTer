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

import axon.cpu.m68k._
import axon.gpu._
import axon.mem._
import cave.gpu._
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
      val generateFrame = Output(Bool())
      val bufferSelect = Output(Bool())
    })

    override def desiredName = "cave"
  }

  // M68000 CPU
  val cpu = withClockAndReset(io.cpuClock, io.cpuReset) { Module(new CPU) }

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

  // Layer 0 RAM
  val layer0Ram = withClockAndReset(io.cpuClock, io.cpuReset) {
    Module(new TrueDualPortRam(
      addrWidthA = Config.LAYER_0_RAM_ADDR_WIDTH,
      dataWidthA = Config.LAYER_0_RAM_DATA_WIDTH,
      addrWidthB = Config.LAYER_0_RAM_GPU_ADDR_WIDTH,
      dataWidthB = Config.LAYER_0_RAM_GPU_DATA_WIDTH
    ))
  }
  layer0Ram.io.clockB := clock

  // Layer 1 RAM
  val layer1Ram = withClockAndReset(io.cpuClock, io.cpuReset) {
    Module(new TrueDualPortRam(
      addrWidthA = Config.LAYER_1_RAM_ADDR_WIDTH,
      dataWidthA = Config.LAYER_1_RAM_DATA_WIDTH,
      addrWidthB = Config.LAYER_1_RAM_GPU_ADDR_WIDTH,
      dataWidthB = Config.LAYER_1_RAM_GPU_DATA_WIDTH
    ))
  }
  layer1Ram.io.clockB := clock

  // Layer 2 RAM
  //
  // The layer 2 RAM masks address bits 14 and 15 on the CPU-side (i.e. the RAM is 8KB mirrored to 64KB).
  //
  // https://github.com/mamedev/mame/blob/master/src/mame/drivers/cave.cpp#L495
  val layer2Ram = withClockAndReset(io.cpuClock, io.cpuReset) {
    Module(new TrueDualPortRam(
      addrWidthA = Config.LAYER_2_RAM_ADDR_WIDTH,
      dataWidthA = Config.LAYER_2_RAM_DATA_WIDTH,
      addrWidthB = Config.LAYER_2_RAM_GPU_ADDR_WIDTH,
      dataWidthB = Config.LAYER_2_RAM_GPU_DATA_WIDTH
    ))
  }
  layer2Ram.io.clockB := clock

  // Layer 0 info
  val layer0Info = withClockAndReset(io.cpuClock, io.cpuReset) { Module(new LayerInfo) }
  layer0Info.io.clockB := clock

  // Layer 1 info
  val layer1Info = withClockAndReset(io.cpuClock, io.cpuReset) { Module(new LayerInfo) }
  layer1Info.io.clockB := clock

  // Layer 2 info
  val layer2Info = withClockAndReset(io.cpuClock, io.cpuReset) { Module(new LayerInfo) }
  layer2Info.io.clockB := clock

  // Palette RAM
  val paletteRam = withClockAndReset(io.cpuClock, io.cpuReset) {
    Module(new TrueDualPortRam(
      addrWidthA = Config.PALETTE_RAM_ADDR_WIDTH,
      dataWidthA = Config.PALETTE_RAM_DATA_WIDTH,
      addrWidthB = Config.PALETTE_RAM_GPU_ADDR_WIDTH,
      dataWidthB = Config.PALETTE_RAM_GPU_DATA_WIDTH
    ))
  }
  paletteRam.io.clockB := clock

  // GPU
  val gpu = Module(new GPU)
  gpu.io.tileRom <> io.tileRom
  gpu.io.spriteRam <> spriteRam.io.portB
  gpu.io.layer0Ram <> layer0Ram.io.portB
  gpu.io.layer1Ram <> layer1Ram.io.portB
  gpu.io.layer2Ram <> layer2Ram.io.portB
  gpu.io.layer0Info <> layer0Info.io.portB
  gpu.io.layer1Info <> layer1Info.io.portB
  gpu.io.layer2Info <> layer2Info.io.portB
  gpu.io.paletteRam <> paletteRam.io.portB
  gpu.io.frameBuffer <> io.frameBuffer


  // Cave
  val cave = Module(new CaveBlackBox)
  cave.io.rst_i := reset
  cave.io.clk_i := clock
  cave.io.clk_68k_i := io.cpuClock
  cave.io.rst_68k_i := io.cpuReset
  cave.io.vblank_i := io.video.vBlank
  cave.io.player <> io.player
  cave.io.cpu <> cpu.io
  cpu.io.dtack := cave.io.memBus.ack
  cpu.io.din := cave.io.memBus.data
  gpu.io.generateFrame := cave.io.generateFrame
  gpu.io.bufferSelect := cave.io.bufferSelect

  // Memory map
  withClockAndReset(io.cpuClock, io.cpuReset) {
    cpu.memMap(0x000000 to 0x0fffff).romT(io.progRom) { _ + Config.PROG_ROM_OFFSET.U }
    cpu.memMap(0x100000 to 0x10ffff).ram(mainRam.io)
    cpu.memMap(0x400000 to 0x40ffff).ram(spriteRam.io.portA)
    cpu.memMap(0x500000 to 0x507fff).ram(layer0Ram.io.portA)
    cpu.memMap(0x600000 to 0x607fff).ram(layer1Ram.io.portA)
    cpu.memMap(0x700000 to 0x70ffff).ram(layer2Ram.io.portA)
    cpu.memMap(0x900000 to 0x900005).ram(layer0Info.io.portA)
    cpu.memMap(0xa00000 to 0xa00005).ram(layer1Info.io.portA)
    cpu.memMap(0xb00000 to 0xb00005).ram(layer2Info.io.portA)
    cpu.memMap(0xc00000 to 0xc0ffff).ram(paletteRam.io.portA)
  }

  // Outputs
  io.tileRom.addr := gpu.io.tileRom.addr + Config.TILE_ROM_OFFSET.U
  io.debug.pc := cpu.io.debug.pc
  io.debug.pcw := cpu.io.debug.pcw
}
