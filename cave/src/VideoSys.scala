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

import arcadia._
import arcadia.gfx._
import arcadia.mem.{AsyncWriteMemIO, RegisterFile}
import arcadia.mister._
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
    /** Programming port */
    val prog = new Bundle {
      /** Video registers download port */
      val video = Flipped(AsyncWriteMemIO(IOCTL.ADDR_WIDTH, IOCTL.DATA_WIDTH))
      /** Asserted when the video registers have been downloaded */
      val done = Input(Bool())
    }
    /** Options port */
    val options = OptionsIO()
    /** Video port */
    val video = Output(VideoIO())
  })

  // Connect IOCTL to video register file
  val videoRegs = Module(new RegisterFile(IOCTL.DATA_WIDTH, VideoSys.VIDEO_REGS_COUNT))
  videoRegs.io.mem <> io.prog.video
    .mapAddr { a => (a >> 1).asUInt } // convert from byte address
    .mapData(Util.swapEndianness) // swap bytes
    .asMemIO

  val timing = withClockAndReset(io.videoClock, io.videoReset) {
    // Original video timing
    val originalVideoTiming = Module(new VideoTiming(Config.originalVideoTimingConfig))
    originalVideoTiming.io.display := io.video.regs.size
    originalVideoTiming.io.frontPorch := io.video.regs.frontPorch
    originalVideoTiming.io.retrace := io.video.regs.retrace

    // Compatibility video timing
    val compatibilityVideoTiming = Module(new VideoTiming(Config.compatibilityVideoTimingConfig))
    compatibilityVideoTiming.io.display := io.video.regs.size
    compatibilityVideoTiming.io.frontPorch := io.video.regs.frontPorch
    compatibilityVideoTiming.io.retrace := io.video.regs.retrace

    // Changing the CRT offset during the display region can momentarily alter the screen
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

  // Outputs
  io.video.clockEnable := timing.clockEnable
  io.video.displayEnable := timing.displayEnable
  io.video.pos := timing.pos
  io.video.hSync := timing.hSync
  io.video.vSync := timing.vSync
  io.video.hBlank := timing.hBlank
  io.video.vBlank := timing.vBlank
  io.video.regs := RegEnable(VideoRegs.decode(videoRegs.io.regs), VideoSys.DEFAULT_REGS, io.prog.done)
  io.video.changeMode := io.prog.done || (io.options.compatibility ^ RegNext(io.options.compatibility))
}

object VideoSys {
  /** The number of video registers */
  val VIDEO_REGS_COUNT = 8

  /** Default video registers */
  val DEFAULT_REGS = VideoRegs(
    hSize = Config.SCREEN_WIDTH,
    vSize = Config.SCREEN_HEIGHT,
    hFrontPorch = 36,
    vFrontPorch = 12,
    hRetrace = 20,
    vRetrace = 2
  )
}
