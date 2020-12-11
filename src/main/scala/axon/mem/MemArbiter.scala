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

package axon.mem

import chisel3._
import chisel3.util._

/**
 * The memory arbiter routes memory requests from multiple input ports to a single output port.
 *
 * Priority is given to the first input port to make a request.
 *
 * @param n         Then number of inputs.
 * @param addrWidth The width of the address bus.
 * @param dataWidth The width of the data bus.
 */
class MemArbiter(n: Int, addrWidth: Int, dataWidth: Int) extends Module {
  val io = IO(new Bundle {
    /** Input port */
    val in = Flipped(Vec(n, BurstReadWriteMemIO(addrWidth, dataWidth)))
    /** Output port */
    val out = BurstReadWriteMemIO(addrWidth, dataWidth)
  })

  // Registers
  val pending = RegInit(false.B)
  val pendingIndex = RegInit(0.U)

  // Encode input requests to a one-hot encoded value
  val index = VecInit(PriorityEncoderOH(io.in.map(mem => mem.rd || mem.wr))).asUInt

  // Set the selected index value
  val selectedIndex = Mux(pending, pendingIndex, index)

  // Toggle the pending registers
  when(!pending && index.orR && !io.out.waitReq) {
    pending := true.B
    pendingIndex := index
  }.elsewhen(io.out.burstDone) {
    pending := false.B
  }

  // Mux the selected input port to the output port
  io.out <> BurstReadWriteMemIO.mux1H(selectedIndex, io.in)

  printf(p"MemArbiter(selectedIndex: $selectedIndex, pending: $pending)\n")
}
