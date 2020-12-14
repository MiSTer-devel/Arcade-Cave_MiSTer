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

package axon

import axon.mem._
import chisel3._

/**
 * Transfers a memory interface between clock domains.
 *
 * Signals transferred from the fast clock domain are stretched so that they can be latched by flops
 * in the slow clock domain. Signals transferred from the slow clock domain are unchanged.
 *
 * The data freezer requires that the clock domain frequencies are phase-aligned integer multiples
 * of each other. This ensures that the signals can be transferred between the clock domains without
 * data loss.
 *
 * @param addrWidth The width of the address bus.
 * @param dataWidth The width of the data bus.
 */
class DataFreezer(addrWidth: Int, dataWidth: Int) extends Module {
  val io = IO(new Bundle {
    /** Target clock domain */
    val targetClock = Input(Clock())
    /** Input port */
    val in = Flipped(AsyncReadMemIO(addrWidth, dataWidth))
    /** Output port */
    val out = AsyncReadMemIO(addrWidth, dataWidth)
  })

  // Detect rising edges of the target clock
  val clear = Util.sync(io.targetClock)

  // Latch wait/valid signals
  val waitReq = !Util.latch(!io.out.waitReq, clear)
  val valid = Util.latch(io.out.valid, clear)

  // Latch valid output data
  val data = Util.latch(io.out.dout, io.out.valid, clear)

  // Latch pending read request until the request has completed
  val pendingRead = Util.latch(RegNext(io.in.rd && !io.out.waitReq), clear && RegNext(valid))

  // Connect I/O ports
  io.in <> io.out

  // Outputs
  io.in.waitReq := waitReq
  io.in.valid := valid
  io.in.dout := data
  io.out.rd := io.in.rd && !pendingRead

  printf(p"DataFreezer(read: ${ io.out.rd }, wait: $waitReq, valid: $valid, clear: $clear)\n")
}

object DataFreezer {
  /**
   * Wraps the given memory interface with a data freezer.
   *
   * @param targetClock The target clock domain.
   * @param mem         The memory interface.
   */
  def freeze(targetClock: Clock)(mem: AsyncReadMemIO): AsyncReadMemIO = {
    val freezer = Module(new DataFreezer(mem.addrWidth, mem.dataWidth))
    freezer.io.targetClock := targetClock
    freezer.io.out <> mem
    freezer.io.in
  }
}
