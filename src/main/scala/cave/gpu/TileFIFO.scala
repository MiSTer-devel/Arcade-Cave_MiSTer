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
