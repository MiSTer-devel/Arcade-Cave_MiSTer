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

package axon.mem

import chisel3._
import chisel3.util._

/** Defines signals required for a burst memory. */
trait BurstIO {
  /** The number of words to transfer during a burst. */
  val burstLength = Output(UInt(8.W))
  /** A flag indicating whether a burst has finished. */
  val burstDone = Input(Bool())
}

/** Defines interface conversion methods. */
trait ConvertBurstMemIO {
  /** Converts the interface to read-write. */
  def asBurstReadWriteMemIO: BurstReadWriteMemIO
}

/**
 * A flow control interface for reading from burst memory.
 *
 * @param addrWidth The width of the address bus.
 * @param dataWidth The width of the data bus.
 */
class BurstReadMemIO(addrWidth: Int, dataWidth: Int) extends AsyncReadMemIO(addrWidth, dataWidth) with BurstIO with ConvertBurstMemIO {
  def this(config: BusConfig) = this(config.addrWidth, config.dataWidth)

  /** Converts the interface to read-write. */
  def asBurstReadWriteMemIO: BurstReadWriteMemIO = {
    val mem = Wire(Flipped(BurstReadWriteMemIO(this)))
    mem.rd := rd
    mem.wr := false.B
    mem.burstLength := burstLength
    waitReq := mem.waitReq
    valid := mem.valid
    burstDone := mem.burstDone
    mem.addr := addr
    mem.mask := DontCare
    mem.din := DontCare
    dout := mem.dout
    mem
  }

  /** Sets default values for all the signals. */
  override def default() = {
    super.default()
    burstLength := 0.U
  }

  /**
   * Maps the address using the given function.
   *
   * @param f The transform function.
   */
  override def mapAddr(f: UInt => UInt): BurstReadMemIO = {
    val mem = Wire(chiselTypeOf(this))
    mem.rd := rd
    mem.burstLength := burstLength
    waitReq := mem.waitReq
    valid := mem.valid
    burstDone := mem.burstDone
    mem.addr := f(addr)
    dout := mem.dout
    mem
  }
}

object BurstReadMemIO {
  def apply(addrWidth: Int, dataWidth: Int) = new BurstReadMemIO(addrWidth, dataWidth)

  def apply(config: BusConfig) = new BurstReadMemIO(config)

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
 * A flow control interface for writing to burst memory.
 *
 * @param addrWidth The width of the address bus.
 * @param dataWidth The width of the data bus.
 */
class BurstWriteMemIO(addrWidth: Int, dataWidth: Int) extends AsyncWriteMemIO(addrWidth, dataWidth) with BurstIO with ConvertBurstMemIO {
  def this(config: BusConfig) = this(config.addrWidth, config.dataWidth)

  /** Converts the interface to read-write. */
  def asBurstReadWriteMemIO: BurstReadWriteMemIO = {
    val mem = Wire(Flipped(BurstReadWriteMemIO(this)))
    mem.rd := false.B
    mem.wr := wr
    mem.burstLength := burstLength
    waitReq := mem.waitReq
    burstDone := mem.burstDone
    mem.addr := addr
    mem.mask := mask
    mem.din := din
    mem
  }

  /** Sets default values for all the signals. */
  override def default() = {
    super.default()
    burstLength := 0.U
  }

  /**
   * Maps the address using the given function.
   *
   * @param f The transform function.
   */
  override def mapAddr(f: UInt => UInt): BurstWriteMemIO = {
    val mem = Wire(chiselTypeOf(this))
    mem.wr := wr
    mem.burstLength := burstLength
    waitReq := mem.waitReq
    burstDone := mem.burstDone
    mem.addr := f(addr)
    mem.mask := mask
    mem.din := din
    mem
  }
}

object BurstWriteMemIO {
  def apply(addrWidth: Int, dataWidth: Int) = new BurstWriteMemIO(addrWidth, dataWidth)

  def apply(config: BusConfig) = new BurstWriteMemIO(config)
}

/**
 * A flow control interface for reading and writing to burst memory.
 *
 * @param addrWidth The width of the address bus.
 * @param dataWidth The width of the data bus.
 */
class BurstReadWriteMemIO(addrWidth: Int, dataWidth: Int) extends AsyncReadWriteMemIO(addrWidth, dataWidth) with BurstIO with ConvertBurstMemIO {
  def this(config: BusConfig) = this(config.addrWidth, config.dataWidth)

  /** Converts the interface to read-only. */
  def asBurstReadMemIO: BurstReadMemIO = {
    val mem = Wire(Flipped(BurstReadMemIO(this)))
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

  /** Converts the interface to write-only. */
  def asBurstWriteMemIO: BurstWriteMemIO = {
    val mem = Wire(Flipped(BurstWriteMemIO(this)))
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

  /** Converts the interface to read-write. */
  def asBurstReadWriteMemIO: BurstReadWriteMemIO = this

  /** Sets default values for all the signals. */
  override def default() = {
    super.default()
    burstLength := 0.U
  }

  /**
   * Maps the address using the given function.
   *
   * @param f The transform function.
   */
  override def mapAddr(f: UInt => UInt): BurstReadWriteMemIO = {
    val mem = Wire(chiselTypeOf(this))
    mem.rd := rd
    mem.wr := wr
    mem.burstLength := burstLength
    waitReq := mem.waitReq
    valid := mem.valid
    burstDone := mem.burstDone
    mem.addr := f(addr)
    mem.mask := mask
    mem.din := din
    dout := mem.dout
    mem
  }
}

object BurstReadWriteMemIO {
  def apply(addrWidth: Int, dataWidth: Int) = new BurstReadWriteMemIO(addrWidth, dataWidth)

  def apply(config: BusConfig) = new BurstReadWriteMemIO(config)

  /**
   * Multiplexes requests from multiple read-write memory interface to a single read-write memory
   * interface. The request is routed to the memory interface with the highest priority.
   *
   * @param in A list of enable-interface pairs.
   */
  def mux1H(in: Seq[(Bool, BurstReadWriteMemIO)]): BurstReadWriteMemIO = {
    val anySelected = in.map(_._1).reduce(_ || _)
    val mem = Wire(chiselTypeOf(in.head._2))
    mem.rd := Mux1H(in.map(a => a._1 -> a._2.rd))
    mem.wr := Mux1H(in.map(a => a._1 -> a._2.wr))
    mem.burstLength := Mux1H(in.map(a => a._1 -> a._2.burstLength))
    mem.addr := Mux1H(in.map(a => a._1 -> a._2.addr))
    mem.mask := Mux1H(in.map(a => a._1 -> a._2.mask))
    mem.din := Mux1H(in.map(a => a._1 -> a._2.din))
    for ((selected, port) <- in) {
      port.waitReq := (anySelected && !selected) || mem.waitReq
      port.valid := selected && mem.valid
      port.burstDone := selected && mem.burstDone
      port.dout := mem.dout
    }
    mem
  }

  /**
   * Multiplexes requests from multiple read-write memory interface to a single read-write memory
   * interfaces. The request is routed to the first enabled interface.
   *
   * @param sel A list of enable signals.
   * @param in  A list of read-write memory interfaces.
   */
  def mux1H(sel: Seq[Bool], in: Seq[BurstReadWriteMemIO]): BurstReadWriteMemIO = mux1H(sel zip in)

  /**
   * Multiplexes requests from multiple read-write memory interface to a single read-write memory
   * interfaces. The request is routed to indexed interface.
   *
   * @param index An index signal.
   * @param in    A list of read-write memory interfaces.
   */
  def mux1H(index: UInt, in: Seq[BurstReadWriteMemIO]): BurstReadWriteMemIO = mux1H(in.indices.map(index(_)), in)
}
