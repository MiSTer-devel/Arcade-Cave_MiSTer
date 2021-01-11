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
import axon.types._
import cave.gpu._
import chisel3._
import chisel3.util._

/**
 * The video subsystem generates the video timing signals and delivers pixel data to the RGB and
 * frame buffer (HDMI) outputs.
 */
class VideoSys extends Module {
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
    /** Asserted when the GPU is ready */
    val gpuReady = Input(Bool())
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
    /** Pixel data port */
    val pixelData = Flipped(DecoupledIO(Bits(Config.ddrConfig.dataWidth.W)))
    /** Frame buffer DMA index */
    val frameBufferDMAIndex = Output(UInt(2.W))
    /** Video DMA index */
    val videoDMAIndex = Output(UInt(2.W))
  })

  // System clock
  val sysClock = clock

  // Video FIFO
  val videoFIFO = Module(new VideoFIFO)
  videoFIFO.io.videoClock := io.videoClock
  videoFIFO.io.videoReset := io.videoReset
  videoFIFO.io.pixelData <> io.pixelData
  videoFIFO.io.rgb <> io.rgb

  // Most of the video timing modules run in the video clock domain
  withClockAndReset(io.videoClock, io.videoReset) {
    // Registers
    val readIndex1 = RegInit(0.U(2.W))
    val readIndex2 = RegInit(0.U(2.W))
    val writeIndex = RegInit(1.U(2.W))

    // Video timing
    val videoTiming = Module(new VideoTiming(Config.videoTimingConfig))
    videoTiming.io.offset := io.offset
    videoTiming.io.video <> io.video
    videoTiming.io.video <> videoFIFO.io.video

    // Toggle read/write index
    when(Util.rising(io.video.vBlank)) {
      when(io.frameBuffer.lowLat) {
        writeIndex := ~writeIndex(0)
      } otherwise {
        writeIndex := nextIndex(writeIndex, readIndex2)
      }
      readIndex1 := writeIndex
    }

    // Toggle read index
    when(Util.rising(io.frameBuffer.vBlank)) {
      when(io.frameBuffer.lowLat) {
        readIndex2 := ~writeIndex(0)
      } otherwise {
        readIndex2 := nextIndex(readIndex2, writeIndex)
      }
    }

    // Set MiSTer frame buffer signals
    io.frameBuffer.enable := true.B
    io.frameBuffer.hSize := Mux(io.rotate, Config.SCREEN_HEIGHT.U, Config.SCREEN_WIDTH.U)
    io.frameBuffer.vSize := Mux(io.rotate, Config.SCREEN_WIDTH.U, Config.SCREEN_HEIGHT.U)
    io.frameBuffer.format := mister.FrameBufferIO.FORMAT_32BPP.U
    io.frameBuffer.base := Config.FRAME_BUFFER_OFFSET.U + (readIndex2 ## 0.U(19.W))
    io.frameBuffer.stride := Mux(io.rotate, (Config.SCREEN_HEIGHT * 4).U, (Config.SCREEN_WIDTH * 4).U)
    io.frameBuffer.forceBlank := false.B

    // Transfer buffer indices to system clock domain
    io.frameBufferDMAIndex := withClock(sysClock) { ShiftRegister(writeIndex, 2) }
    io.videoDMAIndex := withClock(sysClock) { ShiftRegister(readIndex1, 2) }
  }
}
