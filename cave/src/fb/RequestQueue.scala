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

package cave.fb

import arcadia.mem._
import chisel3._
import chisel3.util._

/**
 * Queues memory write requests and ensures they are eventually written to asynchronous memory.
 *
 * If the memory is busy, then requests will be queued until they can be processed.
 *
 * @param inAddrWidth  The width of the input address bus.
 * @param inDataWidth  The width of the input data bus.
 * @param outAddrWidth The width of the output address bus.
 * @param outDataWidth The width of the output data bus.
 * @param depth        The depth of the queue.
 */
class RequestQueue(inAddrWidth: Int, inDataWidth: Int, outAddrWidth: Int, outDataWidth: Int, depth: Int) extends Module {
  // Sanity check
  assert(outAddrWidth % inDataWidth == 0, "Output data width must be divisible by input data width")

  val io = IO(new Bundle {
    /** Enable the request queue */
    val enable = Input(Bool())
    /** Read clock domain */
    val readClock = Input(Clock())
    /** Input port */
    val in = Flipped(new WriteMemIO(inAddrWidth, inDataWidth))
    /** Output port */
    val out = BurstWriteMemIO(outAddrWidth, outDataWidth)
  })

  // The number of words per DDR word
  val dataWidthRatio = outDataWidth / inDataWidth

  // FIFO
  val fifo = Module(new DualClockFIFO(io.in.getWidth, depth))
  fifo.io.readClock := io.readClock

  // Input -> FIFO
  fifo.io.enq.valid := io.in.wr
  fifo.io.enq.bits := io.in.asUInt

  // FIFO -> Output
  io.out.wr := io.enable && fifo.io.deq.valid
  fifo.io.deq.ready := !io.out.waitReq
  val request = fifo.io.deq.bits.asTypeOf(new WriteMemIO(inAddrWidth, inDataWidth))
  io.out.burstLength := 1.U
  io.out.addr := request.addr << (3 - log2Ceil(dataWidthRatio)) // convert to byte address
  io.out.din := Cat(Seq.fill(dataWidthRatio) { request.din })

  if (inDataWidth == 8) {
    io.out.mask := MuxLookup(request.addr(2, 0), request.mask, Seq(
      1.U -> (request.mask << 1),
      2.U -> (request.mask << 2),
      3.U -> (request.mask << 3),
      4.U -> (request.mask << 4),
      5.U -> (request.mask << 5),
      6.U -> (request.mask << 6),
      7.U -> (request.mask << 7),
    ))
  } else if (inDataWidth == 16) {
    io.out.mask := MuxLookup(request.addr(1, 0), request.mask, Seq(
      1.U -> (request.mask << 2),
      2.U -> (request.mask << 4),
      3.U -> (request.mask << 6),
    ))
  } else if (inDataWidth == 32) {
    io.out.mask := Mux(request.addr(0), request.mask << 4, request.mask)
  } else if (inDataWidth == 64) {
    io.out.mask := request.mask
  }
}
