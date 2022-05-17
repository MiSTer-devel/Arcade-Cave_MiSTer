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

package axon.mem

import chisel3._

/**
 * Synchronizes access to an asynchronous read-only memory device running in another clock domain.
 *
 * @param addrWidth The width of the address bus.
 * @param dataWidth The width of the data bus.
 * @param depth     The depth of the FIFO.
 */
class Crossing(addrWidth: Int, dataWidth: Int, depth: Int = 4) extends Module {
  val io = IO(new Bundle {
    /** The target clock domain. */
    val targetClock = Input(Clock())
    /** A memory interface from a device running in the target clock domain. */
    val in = Flipped(AsyncReadMemIO(addrWidth, dataWidth))
    /** A memory interface to a device running in the default clock domain. */
    val out = AsyncReadMemIO(addrWidth, dataWidth)
  })

  // Address FIFO
  val addrFifo = withClock(io.targetClock) { Module(new DualClockFIFO(addrWidth, depth)) }
  addrFifo.io.readClock := clock

  // Data FIFO
  val dataFifo = withClock(clock) { Module(new DualClockFIFO(dataWidth, depth)) }
  dataFifo.io.readClock := io.targetClock

  // input port -> address FIFO
  addrFifo.io.enq.valid := io.in.rd
  io.in.waitReq := !addrFifo.io.enq.ready
  addrFifo.io.enq.bits := io.in.addr

  // address FIFO -> output port
  io.out.rd := addrFifo.io.deq.valid
  addrFifo.io.deq.ready := !io.out.waitReq
  io.out.addr := addrFifo.io.deq.bits

  // output port -> data FIFO
  dataFifo.io.enq.valid := io.out.valid
  dataFifo.io.enq.bits := io.out.dout

  // data FIFO -> input port
  io.in.valid := dataFifo.io.deq.valid
  io.in.dout := dataFifo.io.deq.deq()
}

object Crossing {
  /**
   * Wraps the given asynchronous read-only memory interface with a `ClockDomain` module.
   *
   * @param targetClock The target clock domain.
   * @param mem         The memory interface.
   */
  def syncronize(targetClock: Clock, mem: AsyncReadMemIO): AsyncReadMemIO = {
    val freezer = Module(new Crossing(mem.addrWidth, mem.dataWidth))
    freezer.io.targetClock := targetClock
    freezer.io.out <> mem
    freezer.io.in
  }
}
