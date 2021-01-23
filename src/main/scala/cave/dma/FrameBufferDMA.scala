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

import axon.Util
import axon.mem.BurstWriteMemIO
import axon.util.Counter
import cave.Config
import cave.types.FrameBufferDMAIO
import chisel3._
import chisel3.util._

/**
 * A direct memory access (DMA) controller used to write pixel data to a frame buffer stored in DDR
 * memory.
 *
 * @param addr        The start address of the transfer.
 * @param numWords    The number of words to transfer.
 * @param burstLength The length of the DDR burst.
 */
class FrameBufferDMA(addr: Long, numWords: Int, burstLength: Int) extends Module {
  /** The number of bursts */
  private val NUM_BURSTS = numWords / burstLength

  val io = IO(new Bundle {
    /** Asserted when the DMA controller is enabled */
    val enable = Input(Bool())
    /** Start the transfer */
    val start = Input(Bool())
    /** Asserted when the DMA controller is ready */
    val ready = Output(Bool())
    /** The index of the frame buffer to write */
    val frameBufferIndex = Input(UInt(2.W))
    /** Frame buffer DMA port */
    val frameBufferDMA = new FrameBufferDMAIO
    /** DDR port */
    val ddr = BurstWriteMemIO(Config.ddrConfig.addrWidth, Config.ddrConfig.dataWidth)
  })

  // Registers
  val busyReg = RegInit(false.B)

  // Control signals
  val write = io.enable && busyReg
  val effectiveWrite = write && !io.ddr.waitReq

  // Counters
  val (totalCounter, totalCounterDone) = Counter.static(numWords, enable = effectiveWrite)
  val (burstCounter, burstCounterDone) = Counter.static(NUM_BURSTS, enable = io.ddr.burstDone)

  // Calculate the DDR address
  val ddrAddr = {
    val mask = 0.U(log2Ceil(burstLength * 8).W)
    val offset = io.frameBufferIndex ## burstCounter ## mask
    addr.U + offset
  }

  // Pack pixel data in 64-bit words
  val pixelData = Util.padWords(
    io.frameBufferDMA.dout,
    Config.FRAME_BUFFER_DMA_PIXELS,
    Config.FRAME_BUFFER_DMA_BPP,
    Config.DDR_FRAME_BUFFER_BPP
  )

  // Toggle the busy register
  when(io.start) { busyReg := true.B }.elsewhen(burstCounterDone) { busyReg := false.B }

  // Outputs
  io.ready := !busyReg
  io.frameBufferDMA.rd := true.B
  io.frameBufferDMA.addr := Mux(effectiveWrite, totalCounter +& 1.U, totalCounter)
  io.ddr.wr := write
  io.ddr.addr := ddrAddr
  io.ddr.mask := Fill(io.ddr.maskWidth, 1.U)
  io.ddr.burstLength := burstLength.U
  io.ddr.din := pixelData

  printf(p"FrameBufferDMA(busy: $busyReg, burstCounter: $burstCounter ($burstCounterDone), totalCounter: $totalCounter ($totalCounterDone))\n")
}
