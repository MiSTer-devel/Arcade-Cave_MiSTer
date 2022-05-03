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

import axon.gfx._
import axon.types._
import chisel3._
import chisel3.util._

/**
 * The video subsystem generates video timing signals for both original and compatibility (60Hz)
 * video modes.
 */
class VideoSys extends Module {
  val io = IO(new Bundle {
    /** Video clock domain */
    val videoClock = Input(Clock())
    /** Video reset */
    val videoReset = Input(Bool())
    /** Options port */
    val options = OptionsIO()
    /** Video port */
    val video = VideoIO()
  })

  withClockAndReset(io.videoClock, io.videoReset) {
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

    // Select original or compatibility video timing
    val video = Mux(compatibilityReg, compatibilityVideoTiming.io.video, originalVideoTiming.io.video)
    io.video := video
  }
}
