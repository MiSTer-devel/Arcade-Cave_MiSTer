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

package arcadia.mem

import arcadia.Util
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
  val addrFifo = withClock(io.targetClock) { Module(new DualClockFIFO(UInt(addrWidth.W), depth)) }
  addrFifo.io.readClock := clock

  // Data FIFO
  val dataFifo = withClock(clock) { Module(new DualClockFIFO(Bits(dataWidth.W), depth)) }
  dataFifo.io.readClock := io.targetClock

  // input port -> address FIFO
  addrFifo.io.enq.valid := io.in.rd
  io.in.wait_n := addrFifo.io.enq.ready
  addrFifo.io.enq.bits := io.in.addr

  // address FIFO -> output port
  io.out.rd := addrFifo.io.deq.valid
  addrFifo.io.deq.ready := io.out.wait_n
  io.out.addr := addrFifo.io.deq.bits

  // output port -> data FIFO
  dataFifo.io.enq.valid := io.out.valid
  dataFifo.io.enq.bits := io.out.dout

  // data FIFO -> input port
  io.in.valid := dataFifo.io.deq.valid
  io.in.dout := dataFifo.io.deq.deq()
}

/**
 * Transfers an asynchronous read-only memory interface between clock domains.
 *
 * Signals transferred from the fast clock domain are stretched so that they can be latched in the
 * slow clock domain. Signals transferred from the slow clock domain are unchanged.
 *
 * The data freezer requires that the clock domain frequencies are phase-aligned integer multiples
 * of each other. This ensures that the signals can be transferred between the clock domains without
 * data loss.
 *
 * @param addrWidth The width of the address bus.
 * @param dataWidth The width of the data bus.
 */
class ReadDataFreezer(addrWidth: Int, dataWidth: Int) extends Module {
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
  val wait_n = Util.latch(io.out.wait_n, clear)
  val valid = Util.latch(io.out.valid, clear)

  // Latch valid output data
  val data = Util.latchData(io.out.dout, io.out.valid, clear)

  // Latch pending requests until they have completed
  val pendingRead = RegInit(false.B)
  val effectiveRead = io.in.rd && io.out.wait_n
  val clearRead = clear && RegNext(valid)
  when(effectiveRead) { pendingRead := true.B }.elsewhen(clearRead) { pendingRead := false.B }

  // Connect I/O ports
  io.in <> io.out

  // Outputs
  io.in.wait_n := wait_n
  io.in.valid := valid
  io.in.dout := data
  io.out.rd := io.in.rd && (!pendingRead || clearRead)

  // Debug
  if (sys.env.get("DEBUG").contains("1")) {
    printf(p"ReadDataFreezer(read: ${ io.out.rd }, wait: $wait_n, valid: $valid, clear: $clear)\n")
  }
}

/**
 * Transfers an asynchronous read-write memory interface between clock domains.
 *
 * Signals transferred from the fast clock domain are stretched so that they can be latched in the
 * slow clock domain. Signals transferred from the slow clock domain are unchanged.
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
    val in = Flipped(AsyncMemIO(addrWidth, dataWidth))
    /** Output port */
    val out = AsyncMemIO(addrWidth, dataWidth)
  })

  // Detect rising edges of the target clock
  val clear = Util.sync(io.targetClock)

  // Latch wait/valid signals
  val wait_n = Util.latch(io.out.wait_n, clear)
  val valid = Util.latch(io.out.valid, clear)

  // Latch valid output data
  val data = Util.latchData(io.out.dout, io.out.valid, clear)

  // Latch pending requests until they have completed
  val pendingRead = RegInit(false.B)
  val pendingWrite = RegInit(false.B)
  val effectiveRead = io.in.rd && io.out.wait_n
  val effectiveWrite = io.in.wr && io.out.wait_n
  val clearRead = clear && RegNext(valid)
  val clearWrite = clear
  when(effectiveRead) { pendingRead := true.B }.elsewhen(clearRead) { pendingRead := false.B }
  when(effectiveWrite) { pendingWrite := true.B }.elsewhen(clearWrite) { pendingWrite := false.B }

  // Connect I/O ports
  io.in <> io.out

  // Outputs
  io.in.wait_n := wait_n
  io.in.valid := valid
  io.in.dout := data
  io.out.rd := io.in.rd && (!pendingRead || clearRead)
  io.out.wr := io.in.wr && (!pendingWrite || clearWrite)

  printf(p"DataFreezer(read: ${ io.out.rd }, write: ${ io.out.wr }, wait: $wait_n, valid: $valid, clear: $clear)\n")
}

object Crossing {
  /**
   * Wraps the given asynchronous read-only memory interface with a `ClockDomain` module.
   *
   * @param targetClock The target clock domain.
   * @param mem         The memory interface.
   */
  def syncronize(targetClock: Clock, mem: AsyncReadMemIO): AsyncReadMemIO = {
    val crossing = Module(new Crossing(mem.addrWidth, mem.dataWidth, 4))
    crossing.io.targetClock := targetClock
    crossing.io.out <> mem
    crossing.io.in
  }

  /**
   * Wraps the given memory interface with a data freezer.
   *
   * @param targetClock The target clock domain.
   * @param mem         The read-only memory interface.
   */
  def freeze(targetClock: Clock, mem: AsyncReadMemIO): AsyncReadMemIO = {
    val freezer = Module(new ReadDataFreezer(mem.addrWidth, mem.dataWidth))
    freezer.io.targetClock := targetClock
    freezer.io.out <> mem
    freezer.io.in
  }

  /**
   * Wraps the given memory interface with a data freezer.
   *
   * @param targetClock The target clock domain.
   * @param mem         The read-write memory interface.
   */
  def freeze(targetClock: Clock, mem: AsyncMemIO): AsyncMemIO = {
    val freezer = Module(new DataFreezer(mem.addrWidth, mem.dataWidth))
    freezer.io.targetClock := targetClock
    freezer.io.out <> mem
    freezer.io.in
  }
}
