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

import axon.Util
import axon.mem.{BurstWriteMemIO, ReadMemIO}
import axon.util.Counter
import chisel3._
import chisel3.util._

/**
 * A direct memory access (DMA) controller for writing the frame buffer to DDR.
 *
 * @param numWords The number of words to transfer.
 * @param burstLength The length of the DDR burst.
 * @param addr The start address of the transfer.
 */
class FrameBufferWriteDMA(addr: Long, numWords: Int, burstLength: Int) extends Module {
  /** The number of bursts */
  private val NUM_BURSTS = numWords / burstLength

  val io = IO(new Bundle {
    /** Start the DMA transfer */
    val start = Input(Bool())
    /** Swap the frame buffer */
    val swap = Input(Bool())
    /** Done flag */
    val done = Output(Bool())
    /** Frame buffer port */
    val frameBuffer = ReadMemIO(Config.FRAME_BUFFER_ADDR_WIDTH-2, Config.FRAME_BUFFER_DATA_WIDTH*4)
    /** DDR port */
    val ddr = BurstWriteMemIO(DDRArbiter.ADDR_WIDTH, DDRArbiter.DATA_WIDTH)
  })

  // Registers
  val busyReg = RegInit(false.B)

  // Asserted unless the DDR is busy
  val write = !io.ddr.waitReq && busyReg

  // Asserted when the write will succeed
  val effectiveWrite = busyReg && !io.ddr.waitReq

  // Counters
  val (totalCounterValue, totalCounterDone) = Counter.static(numWords, enable = effectiveWrite)
  val (wordCounterValue, wordCounterDone) = Counter.static(burstLength, enable = effectiveWrite)
  val (burstCounterValue, burstCounterDone) = Counter.static(NUM_BURSTS, enable = wordCounterDone)

  // Calculate the address offset value
  val mask = 0.U(log2Ceil(burstLength*8).W)
  val offset = io.swap ## burstCounterValue ## mask

  // Pad the pixel data, so that four 15-bit pixels pack into a 64-bit DDR word
  val pixelData = Util.padWords(io.frameBuffer.dout, 4, Config.FRAME_BUFFER_DATA_WIDTH, DDRArbiter.DATA_WIDTH/4)

  // Toggle the busy register
  when(io.start) {
    busyReg := true.B
  }.elsewhen(totalCounterDone) {
    busyReg := false.B
  }

  // Outputs
  io.done := RegNext(totalCounterDone, false.B)
  io.frameBuffer.rd := true.B
  io.frameBuffer.addr := Mux(effectiveWrite, totalCounterValue+&1.U, totalCounterValue)
  io.ddr.wr := busyReg
  io.ddr.addr := addr.U + offset
  io.ddr.mask := Fill(io.ddr.maskWidth, 1.U)
  io.ddr.burstCount := burstLength.U
  io.ddr.din := pixelData

  printf(p"FrameBufferWriteDMA(busy: $busyReg, wordCounter: $wordCounterValue ($wordCounterDone), burstCounter: $burstCounterValue ($burstCounterDone), totalCounter: $totalCounterValue ($totalCounterDone))\n")
}
