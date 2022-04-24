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

/** DMA IO */
class DMAIO(config: DMAConfig) extends ReadMemIO(config.addrWidth, config.dataWidth)

object DMAIO {
  def apply(config: DMAConfig) = new DMAIO(config)
}

/**
 * Represents a DMA configuration.
 *
 * @param addrWidth   The width of the DMA address bus.
 * @param dataWidth   The width of the DMA data bus.
 * @param numWords    The number of words to transfer.
 * @param burstLength The length of the DDR burst.
 */
case class DMAConfig(addrWidth: Int = 32,
                     dataWidth: Int = 64,
                     numWords: Int,
                     burstLength: Int = 128) {
  /** The number of bursts. */
  val numBursts = numWords / burstLength
}

/**
 * The direct memory access (DMA) controller copies data from one memory device to another.
 *
 * @param config The DMA configuration.
 */
class DMA(config: DMAConfig) extends Module {
  val io = IO(new Bundle {
    /** Asserted when the DMA controller is enabled */
    val enable = Input(Bool())
    /** Asserted when a DMA transfer should be started */
    val start = Input(Bool())
    /** Asserted when the DMA controller is ready */
    val ready = Output(Bool())
    /** The base address of the target memory device */
    val baseAddr = Input(UInt(config.addrWidth.W))
    /** DMA port */
    val dma = DMAIO(config)
    /** DDR port */
    val ddr = BurstWriteMemIO(config.addrWidth, config.dataWidth)
  })

  // Registers
  val busyReg = RegInit(false.B)

  // Control signals
  val write = io.enable && busyReg
  val effectiveWrite = write && !io.ddr.waitReq

  // Counters
  val (wordCounter, wordCounterDone) = Counter.static(config.numWords, enable = effectiveWrite)
  val (burstCounter, burstCounterDone) = Counter.static(config.numBursts, enable = io.ddr.burstDone)

  // Calculate the DDR memory address
  val ddrAddr = {
    val n = log2Ceil(config.burstLength * 8)
    io.baseAddr + (burstCounter << n).asUInt
  }

  // Toggle the busy register
  when(io.start) { busyReg := true.B }.elsewhen(burstCounterDone) { busyReg := false.B }

  // Outputs
  io.ready := !busyReg
  io.dma.rd := true.B // read-only
  io.dma.addr := Mux(effectiveWrite, wordCounter +& 1.U, wordCounter)
  io.ddr.wr := write
  io.ddr.addr := ddrAddr
  io.ddr.mask := Fill(io.ddr.maskWidth, 1.U)
  io.ddr.burstCount := config.burstLength.U
  io.ddr.din := io.dma.dout

  printf(p"DMA(busy: $busyReg, burstCounter: $burstCounter ($burstCounterDone), wordCounter: $wordCounter ($wordCounterDone))\n")
}
