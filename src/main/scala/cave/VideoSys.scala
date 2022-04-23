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
  val io = IO(new Bundle {
    /** Video clock domain */
    val videoClock = Input(Clock())
    /** Video reset */
    val videoReset = Input(Bool())
    /** Options port */
    val options = OptionsIO()
    /** Asserted when video output should be disabled */
    val forceBlank = Input(Bool())
    /** Frame buffer port */
    val frameBuffer = mister.FrameBufferIO()
    /** The index of the frame buffer write page */
    val frameBufferWritePage = Output(UInt(VideoSys.PAGE_WIDTH.W))
    /** Video port */
    val video = VideoIO()
  })

  // System clock alias
  val sysClock = clock

  withClockAndReset(io.videoClock, io.videoReset) {
    // Registers
    val readIndexReg = RegInit(0.U(VideoSys.PAGE_WIDTH.W))
    val writeIndexReg = RegInit(1.U(VideoSys.PAGE_WIDTH.W))

    // Video timings
    val originalVideoTiming = Module(new VideoTiming(Config.originalVideoTimingConfig))
    val compatibilityVideoTiming = Module(new VideoTiming(Config.compatibilityVideoTimingConfig))

    // Changing the CRT offset value during the display region can momentarily alter the screen
    // dimensions, which may in turn cause issues with the video FIFO. To avoid this problem, the
    // offset value must be latched during a VSYNC.
    originalVideoTiming.io.offset := RegEnable(io.options.offset, originalVideoTiming.io.video.vSync)
    compatibilityVideoTiming.io.offset := RegEnable(io.options.offset, compatibilityVideoTiming.io.video.vSync)

    // The compatibility option should only be latched during a VBLANK, to avoid any sudden changes
    // in the video timing (and possible corruption of the video FIFO).
    val compatibilityReg = RegEnable(io.options.compatibility, originalVideoTiming.io.video.vBlank && compatibilityVideoTiming.io.video.vBlank)

    // Select between original or compatibility video timing
    val video = Mux(compatibilityReg, compatibilityVideoTiming.io.video, originalVideoTiming.io.video)
    video <> io.video

    // Swap the frame buffer pages at the start of a VBLANK
    val swap = Util.rising(io.video.vBlank)

    // Toggle write index
    when(swap) {
      writeIndexReg := Mux(io.frameBuffer.lowLat,
        ~writeIndexReg(0),
        VideoSys.nextIndex(writeIndexReg, readIndexReg)
      )
    }

    // Toggle read index
    when(swap) {
      readIndexReg := Mux(io.frameBuffer.lowLat,
        ~writeIndexReg(0),
        VideoSys.nextIndex(readIndexReg, writeIndexReg)
      )
    }

    // MiSTer frame buffer signals
    io.frameBuffer.enable := true.B
    io.frameBuffer.hSize := Mux(io.options.rotate, Config.SCREEN_HEIGHT.U, Config.SCREEN_WIDTH.U)
    io.frameBuffer.vSize := Mux(io.options.rotate, Config.SCREEN_WIDTH.U, Config.SCREEN_HEIGHT.U)
    io.frameBuffer.format := mister.FrameBufferIO.FORMAT_32BPP.U
    io.frameBuffer.base := Config.FRAME_BUFFER_DDR_OFFSET.U + (readIndexReg ## 0.U(19.W))
    io.frameBuffer.stride := Mux(io.options.rotate, (Config.SCREEN_HEIGHT * 4).U, (Config.SCREEN_WIDTH * 4).U)
    io.frameBuffer.forceBlank := io.forceBlank

    // Set frame buffer write page to the current write index. This value needs to be synchronized
    // back into the system clock domain.
    io.frameBufferWritePage := withClock(sysClock) { ShiftRegister(writeIndexReg, 2) }
  }
}

object VideoSys {
  /** The width of the page index. */
  val PAGE_WIDTH = 2

  /**
   * Returns the next frame buffer index for the given indices.
   *
   * @param a The first index.
   * @param b The second index.
   * @return The next frame buffer index.
   */
  private def nextIndex(a: UInt, b: UInt) =
    MuxCase(1.U, Seq(
      ((a === 0.U && b === 1.U) || (a === 1.U && b === 0.U)) -> 2.U,
      ((a === 1.U && b === 2.U) || (a === 2.U && b === 1.U)) -> 0.U
    ))
}
