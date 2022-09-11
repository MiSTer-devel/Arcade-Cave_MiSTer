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

package arcadia.pocket

import arcadia.Util
import arcadia.mem._
import arcadia.mem.buffer.BurstBuffer
import arcadia.mem.request.WriteRequest
import arcadida.pocket.OptionsIO
import chisel3._
import chisel3.util._

/**
 * The bridge controller buffers write requests from the Pocket and presents them as a burst
 * write-only memory interface.
 *
 * @param addrWidth   The address bus width.
 * @param dataWidth   The data bus width.
 * @param burstLength The number of words to be transferred during a read/write.
 */
class Bridge(addrWidth: Int, dataWidth: Int, burstLength: Int) extends Module {
  val io = IO(new Bundle {
    /** Bridge clock */
    val bridgeClock = Input(Clock())
    /** Bridge interface */
    val bridge = BridgeIO()
    /** Options port */
    val options = OptionsIO()
    /** Download port */
    val download = BurstWriteMemIO(addrWidth, dataWidth)
  })

  // The Pocket bridge writes to the FIFO in the bridge clock domain. The FIFO is read in the system
  // clock domain.
  val fifo = withClock(io.bridgeClock) { Module(new DualClockFIFO(
    new WriteRequest(UInt(BridgeIO.ADDR_WIDTH.W), Bits(BridgeIO.DATA_WIDTH.W)), Bridge.FIFO_DEPTH)
  ) }
  fifo.io.readClock := clock
  fifo.io.enq.bits := WriteRequest(io.bridge.rom)
  fifo.io.enq.valid := io.bridge.rom.wr && io.bridge.rom.addr(31, 28) === 0.U

  // The download buffer is used to buffer ROM data from the bridge, so that complete words are
  // bursted to memory.
  val downloadBuffer = Module(new BurstBuffer(buffer.Config(
    inAddrWidth = BridgeIO.ADDR_WIDTH,
    inDataWidth = BridgeIO.DATA_WIDTH,
    outAddrWidth = addrWidth,
    outDataWidth = dataWidth,
    burstLength = burstLength
  )))
  downloadBuffer.io.in.addr := fifo.io.deq.bits.addr
  downloadBuffer.io.in.din := fifo.io.deq.bits.din
  downloadBuffer.io.in.mask := Fill(BridgeIO.DATA_WIDTH / 8, 1.U)
  downloadBuffer.io.in.wr := fifo.io.deq.valid
  fifo.io.deq.ready := downloadBuffer.io.in.wait_n
  downloadBuffer.io.out <> io.download

  // Swap endianness for writing to registers
  val din = Util.swapEndianness(io.bridge.rom.din)

  /** Returns true if the register at the given address should be latched, false otherwise. */
  def latch(addr: Long): Bool = io.bridge.rom.wr && io.bridge.rom.addr === addr.U

  // Options
  io.options.gameIndex := RegEnable(din, latch(0xf9000000L))
  io.options.scalerMode := RegEnable(din, latch(0xf9000004L))
  io.options.debug := RegEnable(din, latch(0xf9000008L))
  io.options.layer(0) := RegEnable(din, latch(0xf900000cL))
  io.options.layer(1) := RegEnable(din, latch(0xf9000010L))
  io.options.layer(2) := RegEnable(din, latch(0xf9000014L))
  io.options.sprite := RegEnable(din, latch(0xf9000018L))
  io.options.fm := RegEnable(din, latch(0xf900001cL))
  io.options.pcm := RegEnable(din, latch(0xf9000020L))
  io.options.flip := false.B
}

object Bridge {
  /** The depth of the download FIFO in words */
  val FIFO_DEPTH = 16
}
