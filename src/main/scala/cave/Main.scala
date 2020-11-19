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
import axon.gpu._
import axon.mem._
import axon.types._
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
    /** Video port */
    val video = Output(new VideoIO)
    /** DDR port */
    val ddr = new BurstReadWriteMemIO(DDRArbiter.ADDR_WIDTH, DDRArbiter.DATA_WIDTH)
    /** Download port */
    val download = DownloadIO()
    /** Pixel data port */
    val pixelData = DecoupledIO(Bits(DDRArbiter.DATA_WIDTH.W))
    /** Debug port */
    val debug = Output(new Bundle {
      val pc = UInt()
      val pcw = Bool()
    })
  })

  // Registers
  val swapReg = RegInit(false.B)

  // DDR arbiter
  val arbiter = Module(new DDRArbiter)
  arbiter.io.ddr <> io.ddr
  arbiter.io.download <> io.download

  // Frame buffer read DMA
  val fbReadDMA = Module(new FrameBufferReadDMA(
    addr = Config.FRAME_BUFFER_OFFSET,
    numWords = Config.FRAME_BUFFER_DMA_NUM_WORDS,
    burstLength = Config.FRAME_BUFFER_DMA_BURST_LENGTH
  ))
  fbReadDMA.io.swap := swapReg
  fbReadDMA.io.pixelData <> io.pixelData
  fbReadDMA.io.ddr <> arbiter.io.fbFromDDR

  // Frame buffer write DMA
  val fbWriteDMA = Module(new FrameBufferWriteDMA(
    addr = Config.FRAME_BUFFER_OFFSET,
    numWords = Config.FRAME_BUFFER_DMA_NUM_WORDS,
    burstLength = Config.FRAME_BUFFER_DMA_BURST_LENGTH
  ))
  fbWriteDMA.io.swap := !swapReg
  fbWriteDMA.io.ddr <> arbiter.io.fbToDDR

  // Video timing
  //
  // The video timing runs in the video clock domain.
  val videoTiming = withClock(io.videoClock) { Module(new VideoTiming(Config.videoTimingConfig)) }
  io.video := videoTiming.io

  // Cache memory
  //
  // The cache memory runs in the CPU clock domain.
  val cacheMem = withClockAndReset(io.cpuClock, io.cpuReset) {
    Module(new CacheMem(
      inAddrWidth = Config.PROG_ROM_ADDR_WIDTH,
      inDataWidth = Config.PROG_ROM_DATA_WIDTH,
      outAddrWidth = Config.CACHE_ADDR_WIDTH,
      outDataWidth = Config.CACHE_DATA_WIDTH
    ))
  }

  // Data freezer
  val dataFreezer = Module(new DataFreezer(
    addrWidth = Config.CACHE_ADDR_WIDTH,
    dataWidth = Config.CACHE_DATA_WIDTH
  ))
  dataFreezer.io.targetClock := io.cpuClock
  dataFreezer.io.targetReset := io.cpuReset
  dataFreezer.io.in <> cacheMem.io.out
  dataFreezer.io.out <> arbiter.io.cache

  // Cave
  val cave = Module(new Cave)
  cave.io.cpuClock := io.cpuClock
  cave.io.cpuReset := io.cpuReset
  cave.io.player := io.player
  cave.io.progRom <> cacheMem.io.in
  cave.io.tileRom <> arbiter.io.gfx
  cave.io.frameBuffer.dmaDone := fbWriteDMA.io.done
  fbWriteDMA.io.start := cave.io.frameBuffer.dmaStart
  cave.io.video := videoTiming.io

  // Convert the X and Y values to a linear address
  //
  // TODO: Can the video layers do this conversion and use linear addressing?
  val frameBufferAddr = {
    val x = cave.io.frameBuffer.addr.head(log2Up(Config.SCREEN_WIDTH))
    val y = cave.io.frameBuffer.addr.tail(log2Up(Config.SCREEN_WIDTH))
    (y*Config.SCREEN_WIDTH.U)+x
  }

  // Frame buffer
  //
  // The first port of the frame buffer is used by the GPU to buffer pixel data, as the tiles and sprites are rendered.
  // The second port is used to read pixel data, when pixel data is required by the video FIFO.
  val frameBuffer = Module(new DualPortRam(
    addrWidthA = Config.FRAME_BUFFER_ADDR_WIDTH,
    dataWidthA = Config.FRAME_BUFFER_DATA_WIDTH,
    depthA = Some(Config.SCREEN_WIDTH*Config.SCREEN_HEIGHT),
    addrWidthB = Config.FRAME_BUFFER_ADDR_WIDTH-2,
    dataWidthB = Config.FRAME_BUFFER_DATA_WIDTH*4,
    depthB = Some(Config.SCREEN_WIDTH*Config.SCREEN_HEIGHT/4),
    maskEnable = false
  ))
  frameBuffer.io.portA.wr := RegNext(cave.io.frameBuffer.wr)
  frameBuffer.io.portA.addr := RegNext(frameBufferAddr)
  frameBuffer.io.portA.mask := 0.U
  frameBuffer.io.portA.din := RegNext(cave.io.frameBuffer.din)
  frameBuffer.io.portB <> fbWriteDMA.io.frameBuffer

  // Toggle the swap register on the rising edge of the vertical blank signal
  val vBlank = ShiftRegister(videoTiming.io.vBlank, 2)
  when(Util.rising(vBlank)) { swapReg := !swapReg }

  // Outputs
  io.debug.pc := cave.io.debug.pc
  io.debug.pcw := cave.io.debug.pcw
}

object Main extends App {
  (new ChiselStage).execute(
    Array("--compiler", "verilog", "--target-dir", "quartus/rtl", "--output-file", "ChiselTop"),
    Seq(ChiselGeneratorAnnotation(() => new Main))
  )
}
