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
 * Copyright (c) 2022 Josh Bassett
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
    /** Asserted when video output should be disabled */
    val forceBlank = Input(Bool())
    /** Options port */
    val options = OptionsIO()
    /** Video port */
    val video = VideoIO()
    /** RGB output */
    val rgb = Output(RGB(Config.DDR_FRAME_BUFFER_BITS_PER_CHANNEL.W))
    /** Frame buffer port */
    val frameBuffer = mister.FrameBufferIO()
    /** Pixel data port */
    val pixelData = Flipped(DecoupledIO(Bits(Config.ddrConfig.dataWidth.W)))
    /** Frame buffer DMA index */
    val frameBufferDMAIndex = Output(UInt(2.W))
    /** Video DMA index */
    val videoDMAIndex = Output(UInt(2.W))
  })

  // System clock alias
  val sysClock = clock

  // Wires
  val video = Wire(VideoIO())

  // Video FIFO
  val videoFIFO = Module(new VideoFIFO)
  videoFIFO.io.videoClock := io.videoClock
  videoFIFO.io.videoReset := io.videoReset
  videoFIFO.io.pixelData <> io.pixelData
  videoFIFO.io.video <> video

  // Most of the video timing modules run in the video clock domain
  withClockAndReset(io.videoClock, io.videoReset) {
    // Registers
    val readIndex1 = RegInit(0.U(2.W))
    val readIndex2 = RegInit(0.U(2.W))
    val writeIndex = RegInit(1.U(2.W))

    // Video timings
    val originalVideoTiming = Module(new VideoTiming(Config.originalVideoTimingConfig))
    val compatibilityVideoTiming = Module(new VideoTiming(Config.compatibilityVideoTimingConfig))

    // Changing the CRT offset value during the display region can momentarily alter the screen
    // dimensions, which may in turn cause issues with the video FIFO. To avoid this problem, the
    // offset value must be latched during a vertical sync.
    originalVideoTiming.io.offset := RegEnable(io.options.offset, originalVideoTiming.io.video.vSync)
    compatibilityVideoTiming.io.offset := RegEnable(io.options.offset, compatibilityVideoTiming.io.video.vSync)

    // The compatibility option should only be latched during a vertical blank, to avoid any sudden
    // changes in the video timing (and possible corruption of the video FIFO).
    val compatibilityReg = RegEnable(io.options.compatibility, originalVideoTiming.io.video.vBlank && compatibilityVideoTiming.io.video.vBlank)

    // Select between original or compatibility video timing
    video := Mux(compatibilityReg, compatibilityVideoTiming.io.video, originalVideoTiming.io.video)

    // Toggle read/write index
    when(Util.rising(io.video.vBlank)) {
      when(io.frameBuffer.lowLat) {
        writeIndex := ~writeIndex(0)
      } otherwise {
        writeIndex := nextIndex(writeIndex, readIndex2)
      }
      readIndex1 := writeIndex
    }

    // Toggle HDMI read index
    when(Util.rising(io.frameBuffer.vBlank)) {
      when(io.frameBuffer.lowLat) {
        readIndex2 := ~writeIndex(0)
      } otherwise {
        readIndex2 := nextIndex(readIndex2, writeIndex)
      }
    }

    // Disable RGB output when the force blank signal is asserted
    io.rgb := Mux(io.forceBlank, RGB(0.U(Config.DDR_FRAME_BUFFER_BITS_PER_CHANNEL.W)), videoFIFO.io.rgb)

    // MiSTer frame buffer signals
    io.frameBuffer.enable := true.B
    io.frameBuffer.hSize := Mux(io.options.rotate, Config.SCREEN_HEIGHT.U, Config.SCREEN_WIDTH.U)
    io.frameBuffer.vSize := Mux(io.options.rotate, Config.SCREEN_WIDTH.U, Config.SCREEN_HEIGHT.U)
    io.frameBuffer.format := mister.FrameBufferIO.FORMAT_32BPP.U
    io.frameBuffer.base := Config.DDR_FRAME_BUFFER_OFFSET.U + (readIndex2 ## 0.U(19.W))
    io.frameBuffer.stride := Mux(io.options.rotate, (Config.SCREEN_HEIGHT * 4).U, (Config.SCREEN_WIDTH * 4).U)
    io.frameBuffer.forceBlank := io.forceBlank

    // Set DMA frame buffer indices
    io.frameBufferDMAIndex := withClock(sysClock) { ShiftRegister(writeIndex, 2) }
    io.videoDMAIndex := withClock(sysClock) { ShiftRegister(readIndex1, 2) }

    // Set video signals
    io.video <> video
  }
}
