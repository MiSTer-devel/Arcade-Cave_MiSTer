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

package cave.fb

import arcadia._
import arcadia.gfx.VideoIO
import arcadia.mem.BurstMemIO
import arcadia.mister.FrameBufferCtrlIO
import cave._
import chisel3._
import chisel3.util.ShiftRegister

/**
 * The system frame buffer is used for buffering pixel data when the HDMI output is rotated.
 *
 * The MiSTer `screen_rotate` module does not handle DDR memory contention (i.e. it expects
 * exclusive access to the DDR memory). Since this core relies heavily on the DDR memory for sprite
 * rendering and other duties, the built-in screen rotator is not suitable.
 *
 * When the GPU writes a pixel to the system frame buffer in DDR memory, the request could get held
 * up waiting for another large burst to finish. This means that the pixel may not have been written
 * to the frame buffer by the time it's time to write the next one.
 *
 * If we buffer the write requests in a queue while the DDR memory is busy, then we can finish
 * writing them later. The added latency doesn't matter because the system frame buffer uses double
 * buffering.
 */
class SystemFrameBuffer extends Module {
  val io = IO(new Bundle {
    /** Video clock domain */
    val videoClock = Input(Clock())
    /** Enable the frame buffer */
    val enable = Input(Bool())
    /** Rotate the frame buffer 90 degrees */
    val rotate = Input(Bool())
    /** Disable the frame buffer output */
    val forceBlank = Input(Bool())
    /** Video port */
    val video = Input(VideoIO())
    /** Frame buffer control port */
    val frameBufferCtrl = FrameBufferCtrlIO()
    /** Frame buffer port */
    val frameBuffer = Flipped(new SystemFrameBufferIO)
    /** DDR port */
    val ddr = BurstMemIO(Config.ddrConfig.addrWidth, Config.ddrConfig.dataWidth)
  })

  // The page flipper uses triple buffering by default, or double buffering when low latency mode is
  // enabled.
  val pageFlipper = Module(new PageFlipper(Config.SYSTEM_FRAME_BUFFER_BASE_ADDR))
  pageFlipper.io.mode := !io.frameBufferCtrl.lowLat
  pageFlipper.io.swapRead := Util.rising(ShiftRegister(io.frameBufferCtrl.vBlank, 2))
  pageFlipper.io.swapWrite := Util.rising(ShiftRegister(io.video.vBlank, 2))

  // Configure the MiSTer frame buffer
  io.frameBufferCtrl.configure(
    enable = io.enable,
    width = io.video.size.x,
    height = io.video.size.y,
    baseAddr = pageFlipper.io.addrRead,
    rotate = io.rotate,
    forceBlank = io.forceBlank
  )

  // Queue frame buffer requests. Requests are written to the queue in the video clock domain, and
  // read from the queue in the system clock domain
  val queue = withClock(io.videoClock) {
    Module(new RequestQueue(
      inAddrWidth = Config.FRAME_BUFFER_ADDR_WIDTH,
      inDataWidth = Config.SYSTEM_FRAME_BUFFER_DATA_WIDTH,
      outAddrWidth = Config.ddrConfig.addrWidth,
      outDataWidth = Config.ddrConfig.dataWidth,
      depth = Config.SYSTEM_FRAME_BUFFER_REQUEST_QUEUE_DEPTH
    ))
  }
  queue.io.enable := io.enable
  queue.io.readClock := clock
  queue.io.out.mapAddr(_ + pageFlipper.io.addrWrite).asBurstMemIO <> io.ddr

  // Outputs
  io.frameBuffer <> queue.io.in
}
