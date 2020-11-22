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
import cave.dma._
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
    /** Video reset */
    val videoReset = Input(Bool())
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
    /** RGB output */
    val rgb = Output(new RGB(5))
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

  // Video DMA
  val videoDMA = Module(new VideoDMA(
    addr = Config.FRAME_BUFFER_OFFSET,
    numWords = Config.FRAME_BUFFER_DMA_NUM_WORDS,
    burstLength = Config.FRAME_BUFFER_DMA_BURST_LENGTH
  ))
  videoDMA.io.swap := swapReg
  videoDMA.io.ddr <> arbiter.io.fbFromDDR

  // Frame buffer DMA
  val fbDMA = Module(new FrameBufferDMA(
    addr = Config.FRAME_BUFFER_OFFSET,
    numWords = Config.FRAME_BUFFER_DMA_NUM_WORDS,
    burstLength = Config.FRAME_BUFFER_DMA_BURST_LENGTH
  ))
  fbDMA.io.swap := !swapReg
  fbDMA.io.ddr <> arbiter.io.fbToDDR

  // Video timing
  val videoTiming = withClock(io.videoClock) { Module(new VideoTiming(Config.videoTimingConfig)) }
  videoTiming.io <> io.video

  // Video FIFO
  val videoFIFO = Module(new VideoFIFO)
  videoFIFO.io.videoClock := io.videoClock
  videoFIFO.io.videoReset := io.videoReset
  videoFIFO.io.video <> videoTiming.io
  videoFIFO.io.pixelData <> videoDMA.io.pixelData
  io.rgb := videoFIFO.io.rgb

  // Toggle the swap register on the rising edge of the vertical blank signal
  val vBlank = ShiftRegister(videoTiming.io.vBlank, 2)
  when(Util.rising(vBlank)) { swapReg := !swapReg }

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
  cave.io.tileRom <> arbiter.io.tileRom
  cave.io.video := videoTiming.io
  cave.io.frameBuffer <> fbDMA.io.frameBuffer

  // Start the frame buffer DMA when a frame is complete
  fbDMA.io.start := cave.io.frameDone

  // Debug outputs
  io.debug.pc := cave.io.debug.pc
  io.debug.pcw := cave.io.debug.pcw
}

object Main extends App {
  (new ChiselStage).execute(
    Array("--compiler", "verilog", "--target-dir", "quartus/rtl", "--output-file", "ChiselTop"),
    Seq(ChiselGeneratorAnnotation(() => new Main))
  )
}
