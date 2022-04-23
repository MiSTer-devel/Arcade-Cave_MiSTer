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

package cave.gpu

import axon.Util
import axon.gfx.VideoIO
import axon.types.RGB
import cave.Config
import chisel3._
import chisel3.util._

/**
 * The video FIFO is used to buffer pixel data that is ready to be output to the display.
 *
 * When the video FIFO requires pixel data, it is fetched from DDR memory using a DMA transfer.
 */
class VideoFIFO extends Module {
  /** The depth at which the FIFO should fetch pixel data */
  val THRESHOLD = 120

  val io = IO(new Bundle {
    /** Video clock domain */
    val videoClock = Input(Clock())
    /** Video reset */
    val videoReset = Input(Reset())
    /** Video port */
    val video = Input(VideoIO())
    /** Pixel data port */
    val pixelData = Flipped(DecoupledIO(Bits(Config.ddrConfig.dataWidth.W)))
    /** RGB output */
    val rgb = Output(RGB(Config.DDR_FRAME_BUFFER_BITS_PER_CHANNEL.W))
  })

  class VideoFIFOBlackBox extends BlackBox {
    val io = IO(new Bundle {
      val aclr = Input(Reset())
      val data = Input(Bits(64.W))
      val rdclk = Input(Clock())
      val rdreq = Input(Bool())
      val wrclk = Input(Clock())
      val wrreq = Input(Bool())
      val q = Output(Bits(32.W))
      val rdempty = Output(Bool())
      val wrusedw = Output(UInt(8.W))
    })

    override def desiredName = "video_fifo"
  }

  // Pixel data may be read by the consumer once the video FIFO has been drained and filled. These
  // registers need to be clocked in the video clock domain.
  val drainReg = withClockAndReset(io.videoClock, io.videoReset) { RegInit(false.B) }
  val fillReg = withClockAndReset(io.videoClock, io.videoReset) { RegInit(false.B) }

  // External FIFO module
  val fifo = Module(new VideoFIFOBlackBox)
  fifo.io.aclr := io.videoReset
  fifo.io.rdclk := io.videoClock
  fifo.io.rdreq := io.video.clockEnable && io.video.displayEnable && fillReg
  fifo.io.wrclk := clock
  fifo.io.wrreq := io.pixelData.valid
  fifo.io.data := io.pixelData.bits

  // Toggle drain/fill registers
  withClockAndReset(io.videoClock, io.videoReset) {
    when(Util.falling(io.video.vBlank) && fifo.io.rdempty) { drainReg := true.B }
    when(Util.rising(io.video.vBlank) && !fifo.io.rdempty && drainReg) { fillReg := true.B }
  }

  // Fetch pixel data when the FIFO is almost empty
  io.pixelData.ready := ShiftRegister(drainReg, 2) && fifo.io.wrusedw < THRESHOLD.U

  // Decode a 32-bit pixel (ignoring the first 8 bits)
  io.rgb := {
    val bits = fifo.io.q(Config.DDR_FRAME_BUFFER_BITS_PER_CHANNEL * 3 - 1, 0)
    val channels = Util.decode(bits, 3, Config.DDR_FRAME_BUFFER_BITS_PER_CHANNEL)
    RGB(channels)
  }
}
