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
import chisel3.util._

/**
 * A dual-clock FIFO.
 *
 * The write port runs in the default clock domain, and the read port runs in the read clock domain.
 *
 * @param dataWidth The width of the data bus.
 * @param depth     The depth of the FIFO.
 */
class DualClockFIFO(dataWidth: Int, depth: Int) extends Module {
  val io = IO(new Bundle {
    /** Read clock */
    val readClock = Input(Clock())
    /** Read port */
    val deq = Flipped(DeqIO(Bits(dataWidth.W)))
    /** Write port */
    val enq = Flipped(EnqIO(Bits(dataWidth.W)))
  })

  class WrappedDualClockFIFO extends BlackBox(Map(
    "DATA_WIDTH" -> dataWidth,
    "DEPTH" -> depth
  )) {
    val io = IO(new Bundle {
      val data = Input(Bits(dataWidth.W))
      val rdclk = Input(Clock())
      val rdreq = Input(Bool())
      val wrclk = Input(Clock())
      val wrreq = Input(Bool())
      val q = Output(Bits(dataWidth.W))
      val rdempty = Output(Bool())
      val wrfull = Output(Bool())
    })

    override def desiredName = "dual_clock_fifo"
  }

  val fifo = Module(new WrappedDualClockFIFO)

  // Write port
  fifo.io.wrclk := clock
  fifo.io.wrreq := io.enq.fire
  io.enq.ready := !fifo.io.wrfull // allow writing while the FIFO isn't full
  fifo.io.data := io.enq.bits

  // Read port
  fifo.io.rdclk := io.readClock
  fifo.io.rdreq := io.deq.fire
  io.deq.valid := !fifo.io.rdempty // allow reading while the FIFO isn't empty
  io.deq.bits := fifo.io.q
}
