/*
 *    __   __     __  __     __         __
 *   /\ "-.\ \   /\ \/\ \   /\ \       /\ \
 *   \ \ \-.  \  \ \ \_\ \  \ \ \____  \ \ \____
 *    \ \_\\"\_\  \ \_____\  \ \_____\  \ \_____\
 *     \/_/ \/_/   \/_____/   \/_____/   \/_____/
 *    ______     ______       __     ______     ______     ______
 *   /\  __ \   /\  == \     /\ \   /\  ___\   /\  ___\   /\__  _\
 *   \ \ \/\ \  \ \  __<    _\_\ \  \ \  __\   \ \ \____  \/_/\ \/
 *    \ \_____\  \ \_____\ /\_____\  \ \_____\  \ \_____\    \ \_\
 *     \/_____/   \/_____/ \/_____/   \/_____/   \/_____/     \/_/
 *
 *  https://joshbassett.info
 *  https://twitter.com/nullobject
 *  https://github.com/nullobject
 *
 *  Copyright (c) 2020 Josh Bassett
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
 */

package cave

import axon.gpu.VideoIO
import axon.types.RGB
import chisel3._
import chisel3.util._

/**
 * A queue for buffering pixel data that is ready to be output to the display.
 *
 * Ths video FIFO fetches pixel data from DDR memory, using a DMA transfer.
 */
class VideoFIFO extends Module {
  /** The depth at which the FIFO is considered to be almost empty */
  val ALMOST_EMPTY_THRESHOLD = 120

  val io = IO(new Bundle {
    /** Video clock domain */
    val videoClock = Input(Clock())
    /** Video reset */
    val videoReset = Input(Reset())
    /** Video port */
    val video = Input(new VideoIO)
    /** Pixel data port */
    val pixelData = Flipped(DecoupledIO(Bits(Config.DDR_DATA_WIDTH.W)))
    /** RGB output */
    val rgb = Output(new RGB(Config.SCREEN_BITS_PER_CHANNEL))
  })

  class VideoFIFOBlackBox extends BlackBox {
    val io = IO(new Bundle {
      val aclr = Input(Reset())
      val data = Input(Bits(64.W))
      val rdclk = Input(Clock())
      val rdreq = Input(Bool())
      val wrclk = Input(Clock())
      val wrreq = Input(Bool())
      val q = Output(Bits(16.W))
      val rdempty = Output(Bool())
      val wrusedw = Output(UInt(8.W))
    })

    override def desiredName = "video_fifo"
  }

  val videoLockReg = withClockAndReset(io.videoClock, io.videoReset) { RegInit(false.B) }

  val fifo = Module(new VideoFIFOBlackBox)
  fifo.io.aclr := reset
  fifo.io.data := io.pixelData.bits
  fifo.io.rdclk := io.videoClock
  fifo.io.rdreq := io.video.enable && videoLockReg && !fifo.io.rdempty
  fifo.io.wrclk := clock
  fifo.io.wrreq := io.pixelData.valid

  // Set the video lock register during the blanking region, as soon as the FIFO has some data
  when(io.video.hBlank && io.video.vBlank && !fifo.io.rdempty) { videoLockReg := true.B }

  // Set RGB output
  io.rgb := fifo.io.q.asTypeOf(new RGB(Config.SCREEN_BITS_PER_CHANNEL))

  // Fetch pixel data if the FIFO is almost empty
  io.pixelData.ready := fifo.io.wrusedw < ALMOST_EMPTY_THRESHOLD.U
}
