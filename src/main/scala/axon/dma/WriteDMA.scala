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

package axon.dma

import axon.mem._
import axon.util.Counter
import chisel3.util._
import chisel3._

/**
 * The read direct memory access (DMA) controller copies data from a bursted read-only memory to a
 * write-only memory.
 *
 * @param config The DMA configuration.
 */
class WriteDMA(config: DMAConfig) extends Module {
  assert(config.depth % config.burstCount == 0, s"The number of words to transfer must be divisible by the burst count")

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
    val out = WriteMemIO(config)
  })

  // Registers
  val readWaitReg = RegInit(false.B)
  val writeWaitReg = RegInit(false.B)

  // Control signals
  val busy = readWaitReg || writeWaitReg
  val read = io.enable && readWaitReg
  val write = io.enable && writeWaitReg && RegNext(io.in.valid)
  val latch = io.in.valid
  val effectiveRead = read && !io.in.waitReq

  // Counters
  val (writeAddr, writeAddrWrap) = Counter.static(config.depth, enable = write)
  val (burstCounter, burstCounterWrap) = Counter.static(config.numBursts, enable = io.in.burstDone)

  // Calculate read memory address
  val readAddr = {
    val n = log2Ceil(config.burstCount * 8)
    (burstCounter << n).asUInt
  }

  // Toggle read wait register
  when(burstCounterWrap && effectiveRead) {
    readWaitReg := false.B
  }.elsewhen(io.start && !busy) {
    readWaitReg := true.B
  }

  // Toggle write wait register
  when(writeAddrWrap) {
    writeWaitReg := false.B
  }.elsewhen(latch) {
    writeWaitReg := true.B
  }

  // Outputs
  io.busy := busy
  io.in.rd := read
  io.in.burstCount := config.burstCount.U
  io.in.addr := readAddr
  io.out.wr := write
  io.out.addr := writeAddr
  io.out.din := RegEnable(io.in.dout, latch)
  io.out.mask := Fill(io.out.maskWidth, 1.U)

  printf(p"ReadDMA(start: ${ io.start }, readWait: $readWaitReg, writeWait: $writeWaitReg, busy: $busy, read: $read, write: $write, latch: $latch , inAddr: 0x${ Hexadecimal(io.in.addr) }, inData: 0x${ Hexadecimal(io.in.dout) }, outAddr: 0x${ Hexadecimal(io.out.addr) }, outData: 0x${ Hexadecimal(io.out.din) }, waitReq: ${io.in
    .waitReq}, valid: ${ io.in.valid }, burst: $burstCounter ($burstCounterWrap))\n")
}
