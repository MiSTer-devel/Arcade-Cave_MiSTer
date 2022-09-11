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

package arcadia.mem

import chisel3._
import chisel3.util._

trait WaitIO {
  /** The wait request signal is asserted when the device isn't ready to proceed with a request. */
  val wait_n = Input(Bool())
}

trait ValidIO {
  /** The valid signal is asserted when the output data is valid. */
  val valid = Input(Bool())
}

/** Defines interface conversion methods. */
trait ConvertAsyncMemIO {
  /** Converts the interface to read-write. */
  def asAsyncMemIO: AsyncMemIO
}

/**
 * A flow control interface for reading from asynchronous memory.
 *
 * @param addrWidth The width of the address bus.
 * @param dataWidth The width of the data bus.
 */
class AsyncReadMemIO(addrWidth: Int, dataWidth: Int) extends ReadMemIO(addrWidth, dataWidth) with WaitIO with ValidIO with ConvertAsyncMemIO {
  def this(config: BusConfig) = this(config.addrWidth, config.dataWidth)

  /** Converts the interface to read-write. */
  def asAsyncMemIO: AsyncMemIO = {
    val mem = Wire(Flipped(AsyncMemIO(this)))
    mem.rd := rd
    mem.wr := false.B
    wait_n := mem.wait_n
    valid := mem.valid
    mem.addr := addr
    mem.mask := DontCare
    mem.din := DontCare
    dout := mem.dout
    mem
  }

  /** Converts the interface to synchronous read-write. */
  override def asMemIO: MemIO = {
    val mem = Wire(Flipped(MemIO(this)))
    mem.rd := rd
    mem.wr := false.B
    wait_n := true.B
    mem.addr := addr
    mem.mask := DontCare
    mem.din := DontCare
    mem
  }

  /** Converts the interface to synchronous read-only. */
  def asReadMemIO: ReadMemIO = {
    val mem = Wire(Flipped(ReadMemIO(this)))
    rd := mem.rd
    addr := mem.addr
    mem.dout := dout
    mem
  }

  /**
   * Maps the address using the given function.
   *
   * @param f The transform function.
   */
  override def mapAddr(f: UInt => UInt): AsyncReadMemIO = {
    val mem = Wire(Flipped(AsyncReadMemIO(f(addr).getWidth, this.dataWidth)))
    mem.rd := rd
    wait_n := mem.wait_n
    valid := mem.valid
    mem.addr := f(addr)
    dout := mem.dout
    mem
  }

  /**
   * Maps the data using the given function.
   *
   * @param f The transform function.
   */
  override def mapData(f: Bits => Bits): AsyncReadMemIO = {
    val mem = Wire(Flipped(AsyncReadMemIO(this)))
    mem.rd := rd
    wait_n := mem.wait_n
    valid := mem.valid
    mem.addr := addr
    dout := f(mem.dout)
    mem
  }
}

object AsyncReadMemIO {
  def apply(addrWidth: Int, dataWidth: Int) = new AsyncReadMemIO(addrWidth, dataWidth)

  def apply(config: BusConfig) = new AsyncReadMemIO(config)

  /**
   * Multiplexes requests from multiple read-only memory interfaces to a single read-only memory
   * interface. The request is routed to the memory interface with the highest priority.
   *
   * @param in A list of enable-interface pairs.
   */
  def mux1H(in: Seq[(Bool, AsyncReadMemIO)]): AsyncReadMemIO = {
    val anySelected = in.map(_._1).reduce(_ || _)
    val mem = Wire(chiselTypeOf(in.head._2))
    mem.rd := Mux1H(in.map(a => a._1 -> a._2.rd))
    mem.addr := Mux1H(in.map(a => a._1 -> a._2.addr))
    for ((selected, port) <- in) {
      port.wait_n := (!anySelected || selected) && mem.wait_n
      port.valid := selected && mem.valid
      port.dout := mem.dout
    }
    mem
  }
}

/**
 * A control interface for writing to asynchronous memory.
 *
 * @param addrWidth The width of the address bus.
 * @param dataWidth The width of the data bus.
 */
class AsyncWriteMemIO(addrWidth: Int, dataWidth: Int) extends WriteMemIO(addrWidth, dataWidth) with WaitIO with ConvertAsyncMemIO {
  def this(config: BusConfig) = this(config.addrWidth, config.dataWidth)

  /** Converts the interface to read-write. */
  def asAsyncMemIO: AsyncMemIO = {
    val mem = Wire(Flipped(AsyncMemIO(this)))
    mem.rd := false.B
    mem.wr := wr
    wait_n := mem.wait_n
    mem.addr := addr
    mem.mask := mask
    mem.din := din
    mem
  }

  /** Converts the interface to synchronous read-write. */
  override def asMemIO: MemIO = {
    val mem = Wire(Flipped(MemIO(this)))
    mem.rd := false.B
    mem.wr := wr
    wait_n := true.B
    mem.addr := addr
    mem.mask := mask
    mem.din := din
    mem
  }

  /** Converts the interface to synchronous write-only. */
  def asWriteMemIO: WriteMemIO = {
    val mem = Wire(Flipped(WriteMemIO(this)))
    wr := mem.wr
    addr := mem.addr
    mask := mem.mask
    din := mem.din
    mem
  }

  /**
   * Maps the address using the given function.
   *
   * @param f The transform function.
   */
  override def mapAddr(f: UInt => UInt): AsyncWriteMemIO = {
    val mem = Wire(Flipped(AsyncWriteMemIO(f(addr).getWidth, this.dataWidth)))
    mem.wr := wr
    wait_n := mem.wait_n
    mem.addr := f(addr)
    mem.mask := mask
    mem.din := din
    mem
  }

  /**
   * Maps the data using the given function.
   *
   * @param f The transform function.
   */
  override def mapData(f: Bits => Bits): AsyncWriteMemIO = {
    val mem = Wire(Flipped(AsyncWriteMemIO(this)))
    mem.wr := wr
    wait_n := mem.wait_n
    mem.addr := addr
    mem.mask := mask
    mem.din := f(din)
    mem
  }
}

object AsyncWriteMemIO {
  def apply(addrWidth: Int, dataWidth: Int) = new AsyncWriteMemIO(addrWidth, dataWidth)

  def apply(config: BusConfig) = new AsyncWriteMemIO(config)
}

/**
 * A flow control interface for reading and writing to asynchronous memory.
 *
 * @param addrWidth The width of the address bus.
 * @param dataWidth The width of the data bus.
 */
class AsyncMemIO(addrWidth: Int, dataWidth: Int) extends MemIO(addrWidth, dataWidth) with WaitIO with ValidIO with ConvertAsyncMemIO {
  def this(config: BusConfig) = this(config.addrWidth, config.dataWidth)

  /** Converts the interface to asynchronous read-only. */
  def asAsyncReadMemIO: AsyncReadMemIO = {
    val mem = Wire(Flipped(AsyncReadMemIO(this)))
    rd := mem.rd
    wr := false.B
    mem.wait_n := wait_n
    mem.valid := valid
    addr := mem.addr
    mask := DontCare
    din := DontCare
    mem.dout := dout
    mem
  }

  /** Converts the interface to asynchronous write-only. */
  def asAsyncWriteMemIO: AsyncWriteMemIO = {
    val mem = Wire(Flipped(AsyncWriteMemIO(this)))
    rd := false.B
    wr := mem.wr
    mem.wait_n := wait_n
    addr := mem.addr
    mask := mem.mask
    din := mem.din
    mem
  }

  /** Converts the interface to read-write. */
  def asAsyncMemIO: AsyncMemIO = this

  /**
   * Maps the address using the given function.
   *
   * @param f The transform function.
   */
  override def mapAddr(f: UInt => UInt): AsyncMemIO = {
    val mem = Wire(Flipped(AsyncMemIO(f(addr).getWidth, this.dataWidth)))
    mem.rd := rd
    mem.wr := wr
    wait_n := mem.wait_n
    valid := mem.valid
    mem.addr := f(addr)
    mem.mask := mask
    mem.din := din
    dout := mem.dout
    mem
  }

  /**
   * Maps the data using the given function.
   *
   * @param f The transform function.
   */
  override def mapData(f: Bits => Bits): AsyncMemIO = {
    val mem = Wire(Flipped(AsyncMemIO(this)))
    mem.rd := rd
    mem.wr := wr
    wait_n := mem.wait_n
    valid := mem.valid
    mem.addr := addr
    mem.mask := mask
    mem.din := f(din)
    dout := f(mem.dout)
    mem
  }
}

object AsyncMemIO {
  def apply(addrWidth: Int, dataWidth: Int) = new AsyncMemIO(addrWidth, dataWidth)

  def apply(config: BusConfig) = new AsyncMemIO(config)

  /**
   * Multiplexes requests from multiple read-write memory interface to a single read-write memory
   * interface. The request is routed to the memory interface with the highest priority.
   *
   * @param in A list of enable-interface pairs.
   */
  def mux1H(in: Seq[(Bool, AsyncMemIO)]): AsyncMemIO = {
    val anySelected = in.map(_._1).reduce(_ || _)
    val mem = Wire(chiselTypeOf(in.head._2))
    mem.rd := Mux1H(in.map(a => a._1 -> a._2.rd))
    mem.wr := Mux1H(in.map(a => a._1 -> a._2.wr))
    mem.addr := Mux1H(in.map(a => a._1 -> a._2.addr))
    mem.mask := Mux1H(in.map(a => a._1 -> a._2.mask))
    mem.din := Mux1H(in.map(a => a._1 -> a._2.din))
    for ((selected, port) <- in) {
      port.wait_n := (!anySelected || selected) && mem.wait_n
      port.valid := selected && mem.valid
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
  def mux1H(sel: Seq[Bool], in: Seq[AsyncMemIO]): AsyncMemIO = mux1H(sel zip in)

  /**
   * Multiplexes requests from multiple read-write memory interface to a single read-write memory
   * interfaces. The request is routed to indexed interface.
   *
   * @param index An index signal.
   * @param in    A list of read-write memory interfaces.
   */
  def mux1H(index: UInt, in: Seq[AsyncMemIO]): AsyncMemIO = mux1H(in.indices.map(index(_)), in)
}
