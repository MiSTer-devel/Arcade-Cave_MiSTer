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
 * The write direct memory access (DMA) controller copies data from DDR memory to a memory device.
 *
 * @param config The DMA configuration.
 */
class WriteDMA(config: DMAConfig) extends Module {
  val io = IO(new Bundle {
    /** Asserted when the DMA controller is enabled */
    val enable = Input(Bool())
    /** Asserted when a DMA transfer should be started */
    val start = Input(Bool())
    /** Asserted when the DMA controller is ready */
    val ready = Output(Bool())
    /** DMA port */
    val dma = WriteMemIO(config)
    /** DDR port */
    val ddr = BurstReadMemIO(config)
  })

  // Registers
  val busyReg = RegInit(false.B)

  // Control signals
  val read = io.enable && busyReg
  val effectiveRead = read && !io.ddr.waitReq

  // Counters
  val (wordCounter, wordCounterDone) = Counter.static(config.numWords, enable = effectiveRead)
  val (burstCounter, burstCounterDone) = Counter.static(config.numBursts, enable = io.ddr.burstDone)

  // Calculate the DDR memory address
  val ddrAddr = {
    val n = log2Ceil(config.burstLength * 8)
    (burstCounter << n).asUInt
  }

  // Toggle the busy register
  when(io.start) { busyReg := true.B }.elsewhen(burstCounterDone) { busyReg := false.B }

  // Outputs
  io.ready := !busyReg
  io.dma.wr := true.B // write-only
  io.dma.addr := Mux(effectiveRead, wordCounter +& 1.U, wordCounter)
  io.dma.mask := Fill(io.dma.maskWidth, 1.U)
  io.dma.din := io.ddr.dout
  io.ddr.rd := read
  io.ddr.addr := ddrAddr
  io.ddr.burstCount := config.burstLength.U

  printf(p"WriteDMA(busy: $busyReg, burstCounter: $burstCounter ($burstCounterDone), wordCounter: $wordCounter ($wordCounterDone))\n")
}
