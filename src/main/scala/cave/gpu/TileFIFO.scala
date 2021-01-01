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
 * Copyright (c) 2021 Josh Bassett
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

import cave.Config
import chisel3._
import chisel3.util._

/** A FIFO used to buffer tile ROM data. */
class TileFIFO extends Module {
  val io = IO(new Bundle {
    /** Enqueue data port */
    val enq = Flipped(EnqIO(Bits(Config.TILE_ROM_DATA_WIDTH.W)))
    /** Dequeue data port */
    val deq = Flipped(DeqIO(Bits(Config.TILE_ROM_DATA_WIDTH.W)))
  })

  class TileFIFOBlackBox extends BlackBox {
    val io = IO(new Bundle {
      val clock = Input(Clock())
      val data = Input(Bits(Config.TILE_ROM_DATA_WIDTH.W))
      val rdreq = Input(Bool())
      val wrreq = Input(Bool())
      val almost_full = Output(Bool())
      val empty = Output(Bool())
      val q = Output(Bits(Config.TILE_ROM_DATA_WIDTH.W))
    })

    override def desiredName = "tile_fifo"
  }

  val fifo = Module(new TileFIFOBlackBox)
  fifo.io.clock := clock
  io.enq.ready := !fifo.io.almost_full
  fifo.io.wrreq := io.enq.valid
  fifo.io.data := io.enq.bits
  fifo.io.rdreq := io.deq.ready
  io.deq.valid := !fifo.io.empty
  io.deq.bits := fifo.io.q
}
