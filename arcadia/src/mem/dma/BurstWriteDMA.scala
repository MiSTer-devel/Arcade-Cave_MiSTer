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

package arcadia.mem.dma

import arcadia.mem._
import arcadia.util.Counter
import chisel3.util._
import chisel3._

/**
 * The burst write DMA controller copies data from an asynchronous read-only memory interface to a
 * bursted write-only memory interface.
 *
 * @param config The DMA configuration.
 */
class BurstWriteDMA(config: Config) extends Module {
  // Sanity check
  assert(config.depth % config.burstLength == 0, "The number of words to transfer must be divisible by the burst length")

  val io = IO(new Bundle {
    /** Start a DMA transfer */
    val start = Input(Bool())
    /** Asserted when the DMA controller is busy */
    val busy = Output(Bool())
    /** Read memory port */
    val in = AsyncReadMemIO(config)
    /** Write memory port */
    val out = BurstWriteMemIO(config)
  })

  // Registers
  val readEnableReg = RegInit(false.B)
  val writeEnableReg = RegInit(false.B)
  val readPendingReg = RegInit(false.B)
  val writePendingReg = RegInit(false.B)

  // The FIFO is used to buffer words to be bursted to the write memory port. It is deep enough to
  // hold a full burst so that while a burst is in progress, the data for the next burst can be
  // requested.
  val fifo = Module(new Queue(Bits(config.dataWidth.W), config.burstLength, useSyncReadMem = true, hasFlush = true))
  val fifoAlmostFull = fifo.io.count >= (fifo.entries - 1).U
  val fifoBurstReady = fifo.io.count === config.burstLength.U

  // Control signals
  val busy = readEnableReg || writeEnableReg
  val start = io.start && !busy
  val read = readEnableReg && ((!readPendingReg && fifo.io.enq.ready) || (io.in.valid && !fifoAlmostFull))
  val write = writeEnableReg && (writePendingReg || fifoBurstReady)
  val effectiveRead = read && !io.in.waitReq
  val effectiveWrite = write && !io.out.waitReq

  // Counters
  val (wordCounter, wordCounterWrap) = Counter.static(config.depth, enable = effectiveRead)
  val (burstCounter, burstCounterWrap) = Counter.static(config.numBursts, enable = io.out.burstDone)

  // Calculate read byte address
  val readAddr = {
    val n = log2Ceil(config.byteWidth)
    (wordCounter << n).asUInt
  }

  // Calculate write byte address
  val writeAddr = {
    val n = log2Ceil(config.burstLength * config.byteWidth)
    (burstCounter << n).asUInt
  }

  // Flush the FIFO when a transfer is started
  fifo.flush := start

  // Toggle read/write enable registers
  when(start) { readEnableReg := true.B }.elsewhen(wordCounterWrap) { readEnableReg := false.B }
  when(start) { writeEnableReg := true.B }.elsewhen(burstCounterWrap) { writeEnableReg := false.B }

  // Toggle read pending register
  when(effectiveRead) {
    readPendingReg := true.B
  }.elsewhen(io.in.valid && readPendingReg) {
    readPendingReg := false.B
  }

  // Toggle write pending register
  when(io.out.burstDone) {
    writePendingReg := false.B
  }.elsewhen(effectiveWrite) {
    writePendingReg := true.B
  }

  // Enqueue data in the FIFO when it is available
  when(io.in.valid && readPendingReg) {
    fifo.io.enq.enq(io.in.dout)
  } otherwise {
    fifo.io.enq.noenq()
  }

  // Set FIFO dequeue ready flag
  fifo.io.deq.ready := effectiveWrite

  // Outputs
  io.busy := busy
  io.in.rd := read
  io.in.addr := readAddr
  io.out.wr := write
  io.out.burstLength := config.burstLength.U
  io.out.addr := writeAddr
  io.out.din := fifo.io.deq.bits
  io.out.mask := Fill(io.out.maskWidth, 1.U)

  printf(p"BurstWriteDMA(start: ${ io.start }, rdEnable: $readEnableReg, wrEnable: $writeEnableReg, rdPending: $readPendingReg, wrPending: $writePendingReg, busy: $busy, read: $read, write: $write, inAddr: 0x${ Hexadecimal(io.in.addr) }, inData: 0x${ Hexadecimal(io.in.dout) }, outAddr: 0x${ Hexadecimal(io.out.addr) }, outData: 0x${ Hexadecimal(io.out.din) }, waitReq: ${ io.in.waitReq }, valid: ${ io.in.valid }, burst: $burstCounter ($burstCounterWrap))\n")
}
