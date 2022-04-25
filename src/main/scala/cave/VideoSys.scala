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
import chisel3._
import chisel3.util._

/**
 * The video subsystem handles video timing signals and frame buffer page swapping.
 *
 * If low latency mode is enabled, then double buffering is used. Otherwise, triple buffering will
 * be used for butter-smooth HDMI output.
 */
class VideoSys extends Module {
  val io = IO(new Bundle {
    /** Video clock domain */
    val videoClock = Input(Clock())
    /** Video reset */
    val videoReset = Input(Bool())
    /** Asserted when low latency mode should be used for the frame buffer */
    val lowLat = Input(Bool())
    /** Asserted when the frame buffer output is disabled */
    val forceBlank = Input(Bool())
    /** Options port */
    val options = OptionsIO()
    /** Video port */
    val video = VideoIO()
    /** Frame buffer control port */
    val frameBufferControl = mister.FrameBufferControlIO(Config.SCREEN_WIDTH, Config.SCREEN_HEIGHT)
    /** The index of the frame buffer write page */
    val writePage = Output(UInt(VideoSys.PAGE_WIDTH.W))
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
    // in the video timing.
    val compatibilityReg = RegEnable(io.options.compatibility, originalVideoTiming.io.video.vBlank && compatibilityVideoTiming.io.video.vBlank)

    // Select between original or compatibility video timing
    val video = Mux(compatibilityReg, compatibilityVideoTiming.io.video, originalVideoTiming.io.video)
    video <> io.video

    // Swap write index at the start of analog VBLANK
    when(Util.rising(io.video.vBlank)) {
      writeIndexReg := Mux(io.lowLat,
        ~writeIndexReg(0),
        VideoSys.nextIndex(writeIndexReg, readIndexReg)
      )
    }

    // Swap read index at the start of frame buffer VBLANK
    when(Util.rising(io.frameBufferControl.vBlank)) {
      readIndexReg := Mux(io.lowLat,
        ~writeIndexReg(0),
        VideoSys.nextIndex(readIndexReg, writeIndexReg)
      )
    }

    // Configure the MiSTer frame buffer
    io.frameBufferControl.configure(
      baseAddr = Config.SYSTEM_FRAME_BUFFER_DDR_OFFSET.U(31, 21) ## readIndexReg(1, 0) ## 0.U(19.W),
      rotate = io.options.rotate,
      forceBlank = io.forceBlank
    )

    // Set frame buffer write page index
    io.writePage := withClock(sysClock) { ShiftRegister(writeIndexReg, 2) }
  }
}

object VideoSys {
  /** The width of the page index. */
  private val PAGE_WIDTH = 2

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
