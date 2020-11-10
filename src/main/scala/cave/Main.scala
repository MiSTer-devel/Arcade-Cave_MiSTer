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
import axon.gpu.{VideoIO, VideoTiming}
import axon.mem.DualPortRam
import cave.types._
import chisel3._
import chisel3.util._
import chisel3.stage.{ChiselGeneratorAnnotation, ChiselStage}

/**
 * The top-level module.
 *
 * This module abstracts the rest of the arcade hardware from MiSTer-specific things (e.g. memory
 * multiplexer) that are not part of the original arcade hardware design.
 */
class Main extends Module {
  val io = IO(new Bundle {
    /** Video clock domain */
    val videoClock = Input(Clock())
    /** CPU clock domain */
    val cpuClock = Input(Clock())
    /** CPU reset */
    val cpuReset = Input(Bool())
    /** Player port */
    val player = new PlayerIO
    /** Program ROM port */
    val progRom = new ProgRomIO
    /** Tile ROM port */
    val tileRom = new TileRomIO
    /** Frame buffer port */
    val frameBuffer = new Bundle {
      /** DMA start flag */
      val dmaStart = Output(Bool())
      /** DMA done flag */
      val dmaDone = Input(Bool())
      /** Address bus */
      val addr = Input(UInt(15.W))
      /** Data bus */
      val data = Output(Bits(64.W))
      /** Swap flag */
      val swap = Output(Bool())
    }
    /** Video port */
    val video = Output(new VideoIO)
    /** Debug port */
    val debug = Output(new Bundle {
      val addr = UInt(10.W)
      val pc = UInt(32.W)
      val pcw = Bool()
    })
  })

  // Registers
  val swapReg = RegInit(false.B)

  // Video timing
  val videoTiming = withClock(io.videoClock) { Module(new VideoTiming(Config.videoTimingConfig)) }
  io.video := videoTiming.io

  // Cave
  val cave = Module(new CaveTop)
  cave.io.cpuClock := io.cpuClock
  cave.io.cpuReset := io.cpuReset
  cave.io.player := io.player
  cave.io.progRom <> io.progRom
  cave.io.tileRom <> io.tileRom
  cave.io.frameBuffer.dmaDone := io.frameBuffer.dmaDone
  cave.io.video := videoTiming.io

  // Convert the x/y values to a linear address
  //
  // TODO: Use a vec2 instead of an address.
  val frameBufferAddr = {
    val x = cave.io.frameBuffer.addr.head(log2Up(Config.SCREEN_WIDTH))
    val y = cave.io.frameBuffer.addr.tail(log2Up(Config.SCREEN_WIDTH))
    (y*Config.SCREEN_WIDTH.U)+x
  }

  // Frame buffer
  //
  // The first port of the frame buffer is used by the GPU to buffer pixel data, as the tiles and sprites are rendered.
  // The second port is used to read pixel data, when a new frame is required by the video front-end.
  val frameBuffer = Module(new DualPortRam(
    addrWidthA = Config.FRAME_BUFFER_ADDR_WIDTH,
    dataWidthA = Config.FRAME_BUFFER_DATA_WIDTH,
    depthA = Some(Config.SCREEN_WIDTH*Config.SCREEN_HEIGHT),
    addrWidthB = Config.FRAME_BUFFER_ADDR_WIDTH-2,
    dataWidthB = Config.FRAME_BUFFER_DATA_WIDTH*4,
    depthB = Some(Config.SCREEN_WIDTH*Config.SCREEN_HEIGHT/4)
  ))
  frameBuffer.io.portA.wr := RegNext(cave.io.frameBuffer.wr)
  frameBuffer.io.portA.addr := RegNext(frameBufferAddr)
  frameBuffer.io.portA.din := RegNext(cave.io.frameBuffer.din)
  frameBuffer.io.portB.rd := true.B
  frameBuffer.io.portB.addr := io.frameBuffer.addr

  // Pack four 15-bit pixels into the 64-bit frame buffer data bus
  val frameBufferData = Cat(
    0.U ## frameBuffer.io.portB.dout(59, 45),
    0.U ## frameBuffer.io.portB.dout(44, 30),
    0.U ## frameBuffer.io.portB.dout(29, 15),
    0.U ## frameBuffer.io.portB.dout(14, 0)
  )

  // Toggle the swap register on the rising edge of the vertical blank signal
  val vBlank = ShiftRegister(videoTiming.io.vBlank, 2)
  when(Util.rising(vBlank)) { swapReg := !swapReg }

  // Outputs
  io.progRom.addr := cave.io.progRom.addr + Config.PROG_ROM_OFFSET.U
  io.tileRom.addr := cave.io.tileRom.addr + Config.TILE_ROM_OFFSET.U
  io.frameBuffer.dmaStart := cave.io.frameBuffer.dmaStart
  io.frameBuffer.data := frameBufferData
  io.frameBuffer.swap := swapReg
  io.debug.addr := 0.U
  io.debug.pc := 0.U
  io.debug.pcw := false.B
}

object Main extends App {
  (new ChiselStage).execute(
    Array("--compiler", "verilog", "--target-dir", "quartus/rtl", "--output-file", "ChiselTop"),
    Seq(ChiselGeneratorAnnotation(() => new Main))
  )
}
