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
import axon.gfx._
import axon.mem._
import axon.snd._
import axon.types._
import cave.dma._
import cave.types._
import chisel3._
import chisel3.stage._

/**
 * The top-level module.
 *
 * The main module abstracts the rest of the arcade hardware from MiSTer-specific things (e.g.
 * memory arbiter, frame buffer) that are not part of the original arcade hardware design.
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
    /** CRT offset */
    val offset = Input(new SVec2(Config.SCREEN_OFFSET_WIDTH))
    /** Asserted when the screen is rotated */
    val rotate = Input(Bool())
    /** Asserted when the screen is flipped */
    val flip = Input(Bool())
    /** Video port */
    val video = Output(new VideoIO)
    /** RGB output */
    val rgb = Output(new RGB(Config.DDR_FRAME_BUFFER_BITS_PER_CHANNEL))
    /** Frame buffer port */
    val frameBuffer = new mister.FrameBufferIO
    /** Joystick port */
    val joystick = new JoystickIO
    /** Download port */
    val download = DownloadIO()
    /** Audio port */
    val audio = Output(new Audio(Config.ymzConfig.sampleWidth))
    /** DDR port */
    val ddr = DDRIO(Config.ddrConfig)
    /** SDRAM port */
    val sdram = SDRAMIO(Config.sdramConfig)
    /** Asserted when SDRAM is available */
    val sdramAvailable = Input(Bool())
  })

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
  mem.io.sdramAvailable := io.sdramAvailable

  // Video subsystem
  val videoSys = Module(new VideoSys)
  videoSys.io.videoClock := io.videoClock
  videoSys.io.videoReset := io.videoReset
  videoSys.io.offset := io.offset
  videoSys.io.rotate := io.rotate
  videoSys.io.flip := io.flip
  videoSys.io.video <> io.video
  videoSys.io.frameBuffer <> io.frameBuffer
  videoSys.io.rgb <> io.rgb

  // Frame buffer DMA
  val frameBufferDMA = Module(new FrameBufferDMA(
    addr = Config.FRAME_BUFFER_OFFSET,
    numWords = Config.FRAME_BUFFER_DMA_NUM_WORDS,
    burstLength = Config.FRAME_BUFFER_DMA_BURST_LENGTH
  ))
  frameBufferDMA.io.frameBufferIndex := videoSys.io.frameBufferDMAIndex
  frameBufferDMA.io.ddr <> mem.io.frameBufferDMA

  // Video DMA
  val videoDMA = Module(new VideoDMA(
    addr = Config.FRAME_BUFFER_OFFSET,
    numWords = Config.FRAME_BUFFER_DMA_NUM_WORDS,
    burstLength = Config.FRAME_BUFFER_DMA_BURST_LENGTH
  ))
  videoDMA.io.frameBufferIndex := videoSys.io.videoDMAIndex
  videoDMA.io.pixelData <> videoSys.io.pixelData
  videoDMA.io.ddr <> mem.io.videoDMA

  // Cave
  val cave = Module(new Cave)
  cave.io.cpuClock := io.cpuClock
  cave.io.cpuReset := io.cpuReset
  cave.io.gpuCtrl.frameStart := false.B
  frameBufferDMA.io.start := cave.io.gpuCtrl.dmaStart
  cave.io.gpuCtrl.dmaReady := frameBufferDMA.io.ready
  cave.io.gpuCtrl.rotate := io.rotate
  cave.io.gpuCtrl.flip := io.flip
  cave.io.joystick <> io.joystick
  cave.io.progRom <> DataFreezer.freeze(io.cpuClock) { mem.io.progRom }
  cave.io.soundRom <> DataFreezer.freeze(io.cpuClock) { mem.io.soundRom }
  cave.io.tileRom <> mem.io.tileRom
  cave.io.video <> videoSys.io.video
  cave.io.audio <> io.audio
  cave.io.frameBufferDMA <> frameBufferDMA.io.frameBufferDMA
  videoSys.io.gpuReady := cave.io.gpuCtrl.frameReady
}

object Main extends App {
  (new ChiselStage).execute(
    Array("--compiler", "verilog", "--target-dir", "quartus/rtl", "--output-file", "ChiselTop"),
    Seq(ChiselGeneratorAnnotation(() => new Main))
  )
}
