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

package axon.mem.dma

import axon.mem._
import axon.util.Counter
import chisel3.util._
import chisel3._

/**
 * The read direct memory access (DMA) controller copies data from a bursted read-only memory
 * interface to an asynchronous write-only memory interface.
 *
 * @param config The DMA configuration.
 */
class WriteDMA(config: Config) extends Module {
  // Sanity check
  assert(config.depth % config.burstLength == 0, "The number of words to transfer must be divisible by the burst length")

  val io = IO(new Bundle {
    /** Enable the DMA controller */
    val enable = Input(Bool())
    /** Start a DMA transfer */
    val start = Input(Bool())
    /** Asserted when the DMA controller is busy */
    val busy = Output(Bool())
    /** Read memory port */
    val in = BurstReadMemIO(config)
    /** Write memory port */
    val out = AsyncWriteMemIO(config)
  })

  // Registers
  val enableReadReg = RegInit(false.B)
  val enableWriteReg = RegInit(false.B)
  val pendingReadReg = RegInit(false.B)

  // The FIFO is used to buffer words bursted from the read memory port. It is deep enough two hold
  // two full bursts, so that when the FIFO is half-empty another burst can be requested.
  val fifo = Module(new Queue(Bits(config.dataWidth.W), config.burstLength * 2, flow = true, hasFlush = true))
  fifo.flush := !io.enable

  // Control signals
  val busy = enableReadReg || enableWriteReg
  val read = io.enable && enableReadReg && !pendingReadReg && fifo.io.count <= config.burstLength.U
  val write = io.enable && enableWriteReg && fifo.io.deq.valid
  val effectiveRead = read && !io.in.waitReq
  val effectiveWrite = write && !io.out.waitReq

  // Counters
  val (wordCounter, wordCounterWrap) = Counter.static(config.depth, enable = effectiveWrite)
  val (burstCounter, burstCounterWrap) = Counter.static(config.numBursts, enable = io.in.burstDone)

  // Calculate read byte address
  val readAddr = {
    val n = log2Ceil(config.burstLength * config.byteWidth)
    (burstCounter << n).asUInt
  }

  // Calculate write byte address
  val writeAddr = {
    val n = log2Ceil(config.byteWidth)
    (wordCounter << n).asUInt
  }

  // Toggle read wait register
  when(burstCounterWrap) {
    enableReadReg := false.B
  }.elsewhen(io.start && !busy) {
    enableReadReg := true.B
  }

  // Toggle write wait register
  when(wordCounterWrap) {
    enableWriteReg := false.B
  }.elsewhen(fifo.io.deq.valid) {
    enableWriteReg := true.B
  }

  // Toggle pending read register
  when(io.in.burstDone) {
    pendingReadReg := false.B
  }.elsewhen(effectiveRead) {
    pendingReadReg := true.B
  }

  // Enqueue data in the FIFO when it is available
  when(io.in.valid) {
    fifo.io.enq.enq(io.in.dout)
  } otherwise {
    fifo.io.enq.noenq()
  }

  // Set FIFO dequeue ready flag
  fifo.io.deq.ready := effectiveWrite

  // Outputs
  io.busy := busy
  io.in.rd := read
  io.in.burstLength := config.burstLength.U
  io.in.addr := readAddr
  io.out.wr := write
  io.out.addr := writeAddr
  io.out.din := fifo.io.deq.bits
  io.out.mask := Fill(io.out.maskWidth, 1.U)

  printf(p"WriteDMA(start: ${ io.start }, readWait: $enableReadReg, writeWait: $enableWriteReg, busy: $busy, read: $read, write: $write, inAddr: 0x${ Hexadecimal(io.in.addr) }, inData: 0x${ Hexadecimal(io.in.dout) }, outAddr: 0x${ Hexadecimal(io.out.addr) }, outData: 0x${ Hexadecimal(io.out.din) }, waitReq: ${ io.in.waitReq }, valid: ${ io.in.valid }, burst: $burstCounter ($burstCounterWrap))\n")
}
