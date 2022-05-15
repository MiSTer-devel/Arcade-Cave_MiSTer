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

  val timing = withClockAndReset(io.videoClock, io.videoReset) {
    // Video timings
    val originalVideoTiming = Module(new VideoTiming(Config.originalVideoTimingConfig))
    val compatibilityVideoTiming = Module(new VideoTiming(Config.compatibilityVideoTimingConfig))

    // Changing the analog video offset during the display region can momentarily alter the screen
    // dimensions, which may cause issues with other modules. If we latch the offset during a
    // vertical sync, then we can avoid causing any problems.
    originalVideoTiming.io.offset := RegEnable(io.options.offset, originalVideoTiming.io.timing.vSync)
    compatibilityVideoTiming.io.offset := RegEnable(io.options.offset, compatibilityVideoTiming.io.timing.vSync)

    // The compatibility option is latched during a vertical blank to avoid any sudden changes in
    // the video timing.
    val latchReg = RegEnable(io.options.compatibility, originalVideoTiming.io.timing.vBlank && compatibilityVideoTiming.io.timing.vBlank)

    // Select original or compatibility video timing
    val timing = Mux(latchReg, compatibilityVideoTiming.io.timing, originalVideoTiming.io.timing)

    // Register all video timing signals
    RegNext(timing)
  }

  val video = Wire(new VideoIO)
  video.clock := io.videoClock
  video.reset := io.videoReset
  video.clockEnable := timing.clockEnable
  video.displayEnable := timing.displayEnable
  video.changeMode := io.options.compatibility ^ RegNext(io.options.compatibility)
  video.pos := timing.pos
  video.hSync := timing.hSync
  video.vSync := timing.vSync
  video.hBlank := timing.hBlank
  video.vBlank := timing.vBlank

  // Outputs
  io.video <> video
}
