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
import axon.mem.BurstWriteMemIO
import axon.types._
import cave.fb.PageFlipper
import cave.types.SystemFrameBufferIO
import chisel3._
import chisel3.util._

/** The video subsystem generate video timing signals and frame buffer page swapping. */
class VideoSys extends Module {
  val io = IO(new Bundle {
    /** Video clock domain */
    val videoClock = Input(Clock())
    /** Video reset */
    val videoReset = Input(Bool())
    /** Low latency mode (double buffering) */
    val lowLat = Input(Bool())
    /** Disable the frame buffer output */
    val forceBlank = Input(Bool())
    /** Options port */
    val options = OptionsIO()
    /** Video port */
    val video = VideoIO()
    /** Frame buffer control port */
    val frameBufferControl = mister.FrameBufferControlIO(Config.SCREEN_WIDTH, Config.SCREEN_HEIGHT)
    /** System frame buffer port */
    val systemFrameBuffer = Flipped(new SystemFrameBufferIO)
    /** DDR port */
    val ddr = BurstWriteMemIO(Config.ddrConfig.addrWidth, Config.ddrConfig.dataWidth)
  })

  // System clock alias
  val sysClock = clock

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
    video <> io.video

    // Frame buffer page flipper
    val pageFlipper = Module(new PageFlipper)
    pageFlipper.io.lowLat := io.lowLat
    pageFlipper.io.swapRead := Util.rising(io.frameBufferControl.vBlank)
    pageFlipper.io.swapWrite := Util.rising(io.video.vBlank)

    // Configure the MiSTer frame buffer
    io.frameBufferControl.configure(
      baseAddr = pageFlipper.io.readBaseAddr,
      rotate = io.options.rotate,
      forceBlank = io.forceBlank
    )

    // Write requests to the system frame buffer need to be buffered in a request queue.
    //
    // When the GPU writes a pixel to the frame buffer in DDR memory, the request could get held up
    // waiting for another large burst to finish. This means that the pixel may not have been
    // written to the frame buffer by the time it's time to write the next one.
    //
    // If we queue the write requests while the DDR memory is busy, then we can finish writing them
    // later. The added latency doesn't matter because the system frame buffer uses double
    // buffering.
    val queue = Module(new FrameBufferRequestQueue(Config.SYSTEM_FRAME_BUFFER_REQUEST_QUEUE_DEPTH))
    queue.io.readClock := sysClock
    queue.io.frameBuffer <> io.systemFrameBuffer
    queue.io.ddr.mapAddr(_ + pageFlipper.io.writeBaseAddr) <> io.ddr
  }
}
