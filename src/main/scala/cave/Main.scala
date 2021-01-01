/*
 *   __   __     __  __     __         __
 *  /\ "-.\ \   /\ \/\ \   /\ \       /\ \
 *  \ \ \-.  \  \ \ \_\ \  \ \ \____  \ \ \____
 *   \ \_\\"\_\  \ \_____\  \ \_____\  \ \_____\
 *    \/_/ \/_/   \/_____/   \/_____/   \/_____/
 *   ______     ______       __     ______     ______     ______
 *  /\  __ \   /\  == \     /\ \   /\  ___\   /\  ___\   /\__  _\
 *  \ \ \/\ \  \ \  __<    _\_\ \  \ \  __\   \ \ \____  \/_/\ \/
 *   \ \_____\  \ \_____\ /\_____\  \ \_____\  \ \_____\    \ \_\
 *    \/_____/   \/_____/ \/_____/   \/_____/   \/_____/     \/_/
 *
 * https://joshbassett.info
 * https://twitter.com/nullobject
 * https://github.com/nullobject
 *
 * Copyright (c) 2021 Josh Bassett
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package cave

import axon._
import axon.gpu._
import axon.mem._
import axon.snd._
import axon.types._
import cave.dma._
import cave.gpu._
import cave.types._
import chisel3._
import chisel3.stage._
import chisel3.util._

/**
 * The top-level module.
 *
 * The main module abstracts the rest of the arcade hardware from MiSTer-specific things (e.g.
 * memory arbiter, frame buffer) that are not part of the original arcade hardware design.
 */
class Main extends Module {
  /** Returns the next frame buffer index for the given indices */
  private def nextIndex(a: UInt, b: UInt) =
    MuxCase(1.U, Seq(
      ((a === 0.U && b === 1.U) || (a === 1.U && b === 0.U)) -> 2.U,
      ((a === 1.U && b === 2.U) || (a === 2.U && b === 1.U)) -> 0.U
    ))

  val io = IO(new Bundle {
    /** Video clock domain */
    val videoClock = Input(Clock())
    /** Video reset */
    val videoReset = Input(Bool())
    /** CPU clock domain */
    val cpuClock = Input(Clock())
    /** CPU reset */
    val cpuReset = Input(Bool())
    /** Asserted when the screen is rotated */
    val rotate = Input(Bool())
    /** Asserted when the screen is flipped */
    val flip = Input(Bool())
    /** Player port */
    val player = new PlayerIO
    /** Video port */
    val video = Output(new VideoIO)
    /** Download port */
    val download = DownloadIO()
    /** RGB output */
    val rgb = Output(new RGB(Config.DDR_FRAME_BUFFER_BITS_PER_CHANNEL))
    /** Audio port */
    val audio = Output(new Audio(Config.ymzConfig.sampleWidth))
    /** DDR port */
    val ddr = DDRIO(Config.ddrConfig)
    /** SDRAM port */
    val sdram = SDRAMIO(Config.sdramConfig)
    /** MiSTer frame buffer port */
    val frameBuffer = new mister.FrameBufferIO
  })

  // Registers
  val frameBufferWriteIndex = RegInit(0.U)
  val frameBufferReadIndex = RegInit(0.U)

  // The video timing module runs in the video clock domain. It doesn't use the video reset signal,
  // because the video timing signals should always be generated. Otherwise, the progress bar won't
  // be visible while the core is loading.
  val videoTiming = withClockAndReset(io.videoClock, io.videoReset) {
    Module(new VideoTiming(Config.videoTimingConfig))
  }
  videoTiming.io.offset := SVec2.zero
  io.video := videoTiming.io.video

  // DDR controller
  val ddr = Module(new DDR(Config.ddrConfig))
  ddr.io.ddr <> io.ddr

  // SDRAM controller
  val sdram = Module(new SDRAM(Config.sdramConfig))
  sdram.io.sdram <> io.sdram

  // Memory subsystem
  val mem = Module(new MemSys)
  mem.io.download <> io.download
  mem.io.ddr <> ddr.io.mem
  mem.io.sdram <> sdram.io.mem

  // Frame buffer DMA
  val frameBufferDMA = Module(new FrameBufferDMA(
    addr = Config.FRAME_BUFFER_OFFSET,
    numWords = Config.FRAME_BUFFER_DMA_NUM_WORDS,
    burstLength = Config.FRAME_BUFFER_DMA_BURST_LENGTH
  ))
  frameBufferDMA.io.frameBufferIndex := frameBufferWriteIndex
  frameBufferDMA.io.ddr <> mem.io.frameBufferDMA

  // Video DMA
  val videoDMA = Module(new VideoDMA(
    addr = Config.FRAME_BUFFER_OFFSET,
    numWords = Config.FRAME_BUFFER_DMA_NUM_WORDS,
    burstLength = Config.FRAME_BUFFER_DMA_BURST_LENGTH
  ))
  videoDMA.io.frameBufferIndex := frameBufferReadIndex
  videoDMA.io.ddr <> mem.io.videoDMA

  // Video FIFO
  val videoFIFO = Module(new VideoFIFO)
  videoFIFO.io.videoClock := io.videoClock
  videoFIFO.io.videoReset := io.videoReset
  videoFIFO.io.video <> videoTiming.io.video
  videoFIFO.io.pixelData <> videoDMA.io.pixelData
  io.rgb := videoFIFO.io.rgb

  // Cave
  val cave = Module(new Cave)
  cave.io.cpuClock := io.cpuClock
  cave.io.cpuReset := io.cpuReset
  cave.io.rotate := io.rotate
  cave.io.flip := io.flip
  cave.io.player := io.player
  cave.io.progRom <> DataFreezer.freeze(io.cpuClock) { mem.io.progRom }
  cave.io.soundRom <> DataFreezer.freeze(io.cpuClock) { mem.io.soundRom }
  cave.io.tileRom <> mem.io.tileRom
  cave.io.video <> videoTiming.io.video
  cave.io.audio <> io.audio
  cave.io.frameBufferDMA <> frameBufferDMA.io.frameBufferDMA
  frameBufferDMA.io.start := cave.io.frameDone

  // Update the frame buffer write index after a new frame has been written to DDR memory
  when(Util.falling(frameBufferDMA.io.busy)) {
    frameBufferWriteIndex := nextIndex(frameBufferWriteIndex, frameBufferReadIndex)
  }

  // Update the frame buffer read index after a vertical blank
  val vBlank = ShiftRegister(videoTiming.io.video.vBlank, 2)
  when(Util.rising(vBlank)) {
    frameBufferReadIndex := nextIndex(frameBufferReadIndex, frameBufferWriteIndex)
  }

  // MiSTer frame buffer
  io.frameBuffer.enable := io.rotate
  io.frameBuffer.hSize := Config.SCREEN_HEIGHT.U
  io.frameBuffer.vSize := Config.SCREEN_WIDTH.U
  io.frameBuffer.format := mister.FrameBufferIO.FORMAT_32BPP.U
  io.frameBuffer.base := Config.FRAME_BUFFER_OFFSET.U + (frameBufferReadIndex ## 0.U(19.W))
  io.frameBuffer.stride := (Config.SCREEN_HEIGHT * 4).U
  io.frameBuffer.forceBlank := false.B
}

object Main extends App {
  (new ChiselStage).execute(
    Array("--compiler", "verilog", "--target-dir", "quartus/rtl", "--output-file", "ChiselTop"),
    Seq(ChiselGeneratorAnnotation(() => new Main))
  )
}
