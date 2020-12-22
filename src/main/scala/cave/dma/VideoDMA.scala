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

package cave.dma

import axon.mem.BurstReadMemIO
import axon.util.Counter
import cave.Config
import chisel3._
import chisel3.util._

/**
 * A direct memory access (DMA) controller for reading pixel data from DDR memory.
 *
 * @param numWords    The number of words to transfer.
 * @param burstLength The length of the DDR burst.
 * @param addr        The start address of the transfer.
 */
class VideoDMA(addr: Long, numWords: Int, burstLength: Int) extends Module {
  /** The number of bursts */
  private val NUM_BURSTS = numWords / burstLength

  val io = IO(new Bundle {
    /** Swap the frame buffer */
    val swap = Input(Bool())
    /** Asserted when the DMA controller is busy */
    val busy = Output(Bool())
    /** Pixel data port */
    val pixelData = DecoupledIO(Bits(Config.ddrConfig.dataWidth.W))
    /** DDR port */
    val ddr = BurstReadMemIO(Config.ddrConfig.addrWidth, Config.ddrConfig.dataWidth)
  })

  // Registers
  val busyReg = RegInit(false.B)

  // Counters
  val (burstCounter, burstCounterDone) = Counter.static(NUM_BURSTS, enable = io.ddr.burstDone)

  // Control signals
  val read = io.pixelData.ready && !busyReg

  // Calculate the DDR address
  val ddrAddr = {
    val mask = 0.U(log2Ceil(burstLength * 8).W)
    val offset = io.swap ## burstCounter ## mask
    addr.U + offset
  }

  // Toggle the busy register
  when(read && !io.ddr.waitReq) { busyReg := true.B }.elsewhen(io.ddr.burstDone) { busyReg := false.B }

  // Outputs
  io.busy := busyReg
  io.pixelData.bits := io.ddr.dout
  io.pixelData.valid := io.ddr.valid
  io.ddr.rd := read
  io.ddr.addr := ddrAddr
  io.ddr.burstLength := burstLength.U

  printf(p"VideoDMA(busy: $busyReg, burstCounter: $burstCounter ($burstCounterDone))\n")
}
