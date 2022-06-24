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
import cave._
import chisel3._
import chisel3.util._

/**
 * Queues memory write requests and ensures they are eventually written to DDR memory.
 *
 * If the DDR memory is busy, then requests will be queued until they can be processed.
 *
 * @param addrWidth The width of the address bus.
 * @param dataWidth The width of the data bus.
 * @param depth     The depth of the queue.
 */
class RequestQueue(addrWidth: Int, dataWidth: Int, depth: Int) extends Module {
  val io = IO(new Bundle {
    /** Enable the request queue */
    val enable = Input(Bool())
    /** Read clock domain */
    val readClock = Input(Clock())
    /** Memory port */
    val mem = Flipped(new WriteMemIO(addrWidth, dataWidth))
    /** DDR port */
    val ddr = BurstWriteMemIO(Config.ddrConfig.addrWidth, Config.ddrConfig.dataWidth)
  })

  // The number of words per DDR word
  val dataWidthRatio = Config.ddrConfig.dataWidth / dataWidth

  // FIFO
  val fifo = Module(new DualClockFIFO(io.mem.getWidth, depth))
  fifo.io.readClock := io.readClock

  // Memory -> FIFO
  fifo.io.enq.valid := io.mem.wr
  fifo.io.enq.bits := io.mem.asUInt

  // FIFO -> DDR
  io.ddr.wr := io.enable && fifo.io.deq.valid
  fifo.io.deq.ready := !io.ddr.waitReq
  val request = fifo.io.deq.bits.asTypeOf(new WriteMemIO(addrWidth, dataWidth))
  io.ddr.burstLength := 1.U

  // Outputs
  io.ddr.addr := request.addr << (3 - log2Ceil(dataWidthRatio)) // convert to byte address
  io.ddr.din := Cat(Seq.fill(dataWidthRatio) { request.din })

  if (dataWidth == 8) {
    io.ddr.mask := MuxLookup(request.addr(2, 0), request.mask, Seq(
      1.U -> (request.mask << 1),
      2.U -> (request.mask << 2),
      3.U -> (request.mask << 3),
      4.U -> (request.mask << 4),
      5.U -> (request.mask << 5),
      6.U -> (request.mask << 6),
      7.U -> (request.mask << 7),
    ))
  } else if (dataWidth == 16) {
    io.ddr.mask := MuxLookup(request.addr(1, 0), request.mask, Seq(
      1.U -> (request.mask << 2),
      2.U -> (request.mask << 4),
      3.U -> (request.mask << 6),
    ))
  } else if (dataWidth == 32) {
    io.ddr.mask := Mux(request.addr(0), request.mask << 4, request.mask)
  } else if (dataWidth == 64) {
    io.ddr.mask := request.mask
  }
}
