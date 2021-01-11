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

package axon.mem

import chisel3._
import chisel3.util._

trait BurstIO {
  /** The number of words to transfer during a burst */
  val burstLength = Output(UInt(8.W))
  /** A flag indicating whether a burst has finished */
  val burstDone = Input(Bool())
}

/**
 * A flow control interface for reading from bursted memory.
 *
 * @param addrWidth The width of the address bus.
 * @param dataWidth The width of the data bus.
 */
class BurstReadMemIO protected(addrWidth: Int, dataWidth: Int) extends AsyncReadMemIO(addrWidth, dataWidth) with BurstIO {
  override def cloneType: this.type = new BurstReadMemIO(addrWidth, dataWidth).asInstanceOf[this.type]
}

object BurstReadMemIO {
  def apply(addrWidth: Int, dataWidth: Int) = new BurstReadMemIO(addrWidth, dataWidth)

  /**
   * Multiplexes requests from multiple write-only memory interface to a single write-only memory
   * interfaces. The request is routed to the first enabled interface.
   *
   * @param in A list of enable-interface pairs.
   */
  def mux(in: Seq[(Bool, BurstReadMemIO)]): BurstReadMemIO = {
    val mem = Wire(chiselTypeOf(in.head._2))
    mem.rd := MuxCase(false.B, in.map(a => a._1 -> a._2.rd))
    mem.burstLength := MuxCase(0.U, in.map(a => a._1 -> a._2.burstLength))
    mem.addr := MuxCase(DontCare, in.map(a => a._1 -> a._2.addr))
    for ((selected, port) <- in) {
      port.waitReq := (selected && mem.waitReq) || (!selected && port.rd)
      port.valid := mem.valid && selected
      port.burstDone := mem.burstDone && selected
      port.dout := mem.dout
    }
    mem
  }
}

/**
 * A flow control interface for writing to bursted memory.
 *
 * @param addrWidth The width of the address bus.
 * @param dataWidth The width of the data bus.
 */
class BurstWriteMemIO protected(addrWidth: Int, dataWidth: Int) extends AsyncWriteMemIO(addrWidth, dataWidth) with BurstIO {
  override def cloneType: this.type = new BurstWriteMemIO(addrWidth, dataWidth).asInstanceOf[this.type]
}

object BurstWriteMemIO {
  def apply(addrWidth: Int, dataWidth: Int) = new BurstWriteMemIO(addrWidth, dataWidth)
}

/**
 * A flow control interface for reading and writing to bursted memory.
 *
 * @param addrWidth The width of the address bus.
 * @param dataWidth The width of the data bus.
 */
class BurstReadWriteMemIO protected(addrWidth: Int, dataWidth: Int) extends AsyncReadWriteMemIO(addrWidth, dataWidth) with BurstIO {
  override def cloneType: this.type = new BurstReadWriteMemIO(addrWidth, dataWidth).asInstanceOf[this.type]

  /** Converts the interface to read-only */
  def asBurstReadMemIO: BurstReadMemIO = {
    val mem = Wire(Flipped(BurstReadMemIO(addrWidth, dataWidth)))
    rd := mem.rd
    wr := false.B
    burstLength := mem.burstLength
    mem.waitReq := waitReq
    mem.valid := valid
    mem.burstDone := burstDone
    addr := mem.addr
    mask := DontCare
    din := DontCare
    mem.dout := dout
    mem
  }

  /** Converts the interface to write-only */
  def asBurstWriteMemIO: BurstWriteMemIO = {
    val mem = Wire(Flipped(BurstWriteMemIO(addrWidth, dataWidth)))
    rd := false.B
    wr := mem.wr
    burstLength := mem.burstLength
    mem.waitReq := waitReq
    mem.burstDone := burstDone
    addr := mem.addr
    mask := mem.mask
    din := mem.din
    mem
  }
}

object BurstReadWriteMemIO {
  def apply(addrWidth: Int, dataWidth: Int) = new BurstReadWriteMemIO(addrWidth, dataWidth)

  /**
   * Multiplexes requests from multiple read-write memory interface to a single read-write memory
   * interfaces. The request is routed to the first enabled interface.
   *
   * @param in A list of enable-interface pairs.
   */
  def mux1H(in: Seq[(Bool, BurstReadWriteMemIO)]): BurstReadWriteMemIO = {
    val mem = Wire(chiselTypeOf(in.head._2))
    mem.rd := Mux1H(in.map(a => a._1 -> a._2.rd))
    mem.wr := Mux1H(in.map(a => a._1 -> a._2.wr))
    mem.burstLength := Mux1H(in.map(a => a._1 -> a._2.burstLength))
    mem.addr := Mux1H(in.map(a => a._1 -> a._2.addr))
    mem.mask := Mux1H(in.map(a => a._1 -> a._2.mask))
    mem.din := Mux1H(in.map(a => a._1 -> a._2.din))
    for ((selected, port) <- in) {
      port.waitReq := (selected && mem.waitReq) || (!selected && (port.rd || port.wr))
      port.valid := mem.valid && selected
      port.burstDone := mem.burstDone && selected
      port.dout := mem.dout
    }
    mem
  }

  def mux1H(sel: Seq[Bool], in: Seq[BurstReadWriteMemIO]): BurstReadWriteMemIO = mux1H(sel zip in)

  def mux1H(sel: UInt, in: Seq[BurstReadWriteMemIO]): BurstReadWriteMemIO = mux1H(in.indices.map(sel(_)), in)
}
