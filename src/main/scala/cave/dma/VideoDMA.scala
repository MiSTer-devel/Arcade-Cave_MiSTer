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
 * Copyright (c) 2021 Josh Bassett
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

package cave.dma

import axon.mem.BurstReadMemIO
import axon.util.Counter
import cave.Config
import chisel3._
import chisel3.util._

/**
 * A direct memory access (DMA) controller used to read pixel data from a frame buffer stored in
 * DDR memory.
 *
 * @param numWords    The number of words to transfer.
 * @param burstLength The length of the DDR burst.
 * @param addr        The start address of the transfer.
 */
class VideoDMA(addr: Long, numWords: Int, burstLength: Int) extends Module {
  /** The number of bursts */
  private val NUM_BURSTS = numWords / burstLength

  val io = IO(new Bundle {
    /** Asserted when the DMA controller is busy */
    val busy = Output(Bool())
    /** The index of the frame buffer to read */
    val frameBufferIndex = Input(UInt(2.W))
    /** Pixel data port */
    val pixelData = DecoupledIO(Bits(Config.ddrConfig.dataWidth.W))
    /** DDR port */
    val ddr = BurstReadMemIO(Config.ddrConfig.addrWidth, Config.ddrConfig.dataWidth)
  })

  // Registers
  val busyReg = RegInit(false.B)

  // Counters
  val (burstCounter, burstCounterDone) = Counter.static(NUM_BURSTS, enable = io.ddr.burstDone)

  // Control signals
  val read = io.pixelData.ready && !busyReg

  // Calculate the DDR address
  val ddrAddr = {
    val mask = 0.U(log2Ceil(burstLength * 8).W)
    val offset = io.frameBufferIndex ## burstCounter ## mask
    addr.U + offset
  }

  // Toggle the busy register
  when(read && !io.ddr.waitReq) { busyReg := true.B }.elsewhen(io.ddr.burstDone) { busyReg := false.B }

  // Outputs
  io.busy := busyReg
  io.pixelData.bits := io.ddr.dout
  io.pixelData.valid := io.ddr.valid
  io.ddr.rd := read
  io.ddr.addr := ddrAddr
  io.ddr.burstLength := burstLength.U

  printf(p"VideoDMA(busy: $busyReg, burstCounter: $burstCounter ($burstCounterDone))\n")
}
