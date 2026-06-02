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

package arcadia.mem.arbiter

import arcadia.mem.{AsyncMemIO, ConvertAsyncMemIO}
import chisel3._
import chisel3.util._

/**
 * The memory arbiter multiplexes requests from multiple input ports to a single output port.
 *
 * @param n         Then number of inputs.
 * @param addrWidth The width of the address bus.
 * @param dataWidth The width of the data bus.
 */
class AsyncMemArbiter(n: Int, addrWidth: Int, dataWidth: Int) extends Module {
  val io = IO(new Bundle {
    /** Asserted when the arbiter has a pending request */
    val busy = Output(Bool())
    /** One-hot vector indicating which output was chosen */
    val chosen = Output(UInt(n.W))
    /** Input ports */
    val in = Flipped(Vec(n, AsyncMemIO(addrWidth, dataWidth)))
    /** Output port */
    val out = AsyncMemIO(addrWidth, dataWidth)
  })

  /**
   * Connects the given input ports to the output port.
   *
   * Priority is given to the input ports in descending order.
   *
   * @param in The list of input ports.
   * @return The output port.
   */
  def connect(in: ConvertAsyncMemIO*): AsyncMemIO = {
    assert(in.length == n, s"There must be exactly $n input ports")
    in.zipWithIndex.foreach { case (port, i) => port.asAsyncMemIO <> io.in(i) }
    io.out
  }

  // Registers
  val busyReg = RegInit(false.B)
  val indexReg = RegInit(0.U)

  // Encode input requests to a one-hot encoded value
  val index = VecInit(PriorityEncoderOH(io.in.map(mem => mem.rd || mem.wr))).asUInt

  // Set the chosen index
  val chosen = Mux(busyReg, indexReg, index)

  // Assert the effective request signal when a request is accepted by the output port
  val effectiveRequest = !busyReg && io.out.rd && io.out.wait_n

  // Toggle the busy register
  when(io.out.valid) {
    busyReg := false.B
  }.elsewhen(effectiveRequest) {
    busyReg := true.B
    indexReg := index
  }

  // Outputs
  io.busy := busyReg
  io.chosen := chosen
  io.out <> AsyncMemIO.mux1H(chosen, io.in)

  // Debug
  if (sys.env.get("DEBUG").contains("1")) {
    printf(p"AsyncMemArbiter(chosen: $chosen, busy: $busyReg)\n")
  }
}
