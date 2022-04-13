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
  val data = Util.latchData(io.out.dout, io.out.valid, clear)

  // Latch pending requests until they have completed
  val pendingRead = RegInit(false.B)
  val effectiveRead = io.in.rd && !io.out.waitReq
  val clearRead = clear && RegNext(valid)
  when(effectiveRead) { pendingRead := true.B }.elsewhen(clearRead) { pendingRead := false.B }

  // Connect I/O ports
  io.in <> io.out

  // Outputs
  io.in.waitReq := waitReq
  io.in.valid := valid
  io.in.dout := data
  io.out.rd := io.in.rd && (!pendingRead || clearRead)

  printf(p"DataFreezer(read: ${ io.out.rd }, wait: $waitReq, valid: $valid, clear: $clear)\n")
}

class ReadWriteDataFreezer(addrWidth: Int, dataWidth: Int) extends Module {
  val io = IO(new Bundle {
    /** Target clock domain */
    val targetClock = Input(Clock())
    /** Input port */
    val in = Flipped(AsyncReadWriteMemIO(addrWidth, dataWidth))
    /** Output port */
    val out = AsyncReadWriteMemIO(addrWidth, dataWidth)
  })

  // Detect rising edges of the target clock
  val clear = Util.sync(io.targetClock)

  // Latch wait/valid signals
  val waitReq = !Util.latch(!io.out.waitReq, clear)
  val valid = Util.latch(io.out.valid, clear)

  // Latch valid output data
  val data = Util.latchData(io.out.dout, io.out.valid, clear)

  // Latch pending requests until they have completed
  val pendingRead = RegInit(false.B)
  val pendingWrite = RegInit(false.B)
  val effectiveRead = io.in.rd && !io.out.waitReq
  val effectiveWrite = io.in.wr && !io.out.waitReq
  val clearRead = clear && RegNext(valid)
  val clearWrite = clear
  when(effectiveRead) { pendingRead := true.B }.elsewhen(clearRead) { pendingRead := false.B }
  when(effectiveWrite) { pendingWrite := true.B }.elsewhen(clearWrite) { pendingWrite := false.B }

  // Connect I/O ports
  io.in <> io.out

  // Outputs
  io.in.waitReq := waitReq
  io.in.valid := valid
  io.in.dout := data
  io.out.rd := io.in.rd && (!pendingRead || clearRead)
  io.out.wr := io.in.wr && (!pendingWrite || clearWrite)

  printf(p"DataFreezer(read: ${ io.out.rd }, write: ${ io.out.wr }, wait: $waitReq, valid: $valid, clear: $clear)\n")
}

object DataFreezer {
  /**
   * Wraps the given memory interface with a data freezer.
   *
   * @param targetClock The target clock domain.
   * @param mem         The memory interface.
   */
  def freeze(targetClock: Clock, mem: AsyncReadMemIO): AsyncReadMemIO = {
    val freezer = Module(new DataFreezer(mem.addrWidth, mem.dataWidth))
    freezer.io.targetClock := targetClock
    freezer.io.out <> mem
    freezer.io.in
  }

  def freeze(targetClock: Clock, mem: AsyncReadWriteMemIO): AsyncReadWriteMemIO = {
    val freezer = Module(new ReadWriteDataFreezer(mem.addrWidth, mem.dataWidth))
    freezer.io.targetClock := targetClock
    freezer.io.out <> mem
    freezer.io.in
  }
}
