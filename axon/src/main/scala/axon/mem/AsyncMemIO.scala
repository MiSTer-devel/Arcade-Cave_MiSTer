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

trait WaitIO {
  /** The wait request signal is asserted when the device isn't ready to proceed with a request. */
  val waitReq = Input(Bool())
}

trait ValidIO {
  /** The valid signal is asserted when the output data is valid. */
  val valid = Input(Bool())
}

/**
 * A flow control interface for reading from asynchronous memory.
 *
 * @param addrWidth The width of the address bus.
 * @param dataWidth The width of the data bus.
 */
class AsyncReadMemIO(addrWidth: Int, dataWidth: Int) extends ReadMemIO(addrWidth, dataWidth) with WaitIO with ValidIO {
  def this(config: BusConfig) = this(config.addrWidth, config.dataWidth)

  /**
   * Maps the address using the given function.
   *
   * @param f The transform function.
   */
  override def mapAddr(f: UInt => UInt): AsyncReadMemIO = {
    val mem = Wire(chiselTypeOf(this))
    mem.rd := rd
    waitReq := mem.waitReq
    valid := mem.valid
    mem.addr := f(addr)
    dout := mem.dout
    mem
  }

  /** Converts the interface to synchronous read-write. */
  def asReadMemIO: ReadMemIO = {
    val mem = Wire(Flipped(ReadMemIO(this)))
    mem.rd := rd
    waitReq := false.B
    valid := true.B
    mem.addr := addr
    dout := mem.dout
    mem
  }
}

object AsyncReadMemIO {
  def apply(addrWidth: Int, dataWidth: Int) = new AsyncReadMemIO(addrWidth, dataWidth)

  def apply(config: BusConfig) = new AsyncReadMemIO(config)
}

/**
 * A control interface for writing to asynchronous memory.
 *
 * @param addrWidth The width of the address bus.
 * @param dataWidth The width of the data bus.
 */
class AsyncWriteMemIO(addrWidth: Int, dataWidth: Int) extends WriteMemIO(addrWidth, dataWidth) with WaitIO {
  def this(config: BusConfig) = this(config.addrWidth, config.dataWidth)

  /**
   * Maps the address using the given function.
   *
   * @param f The transform function.
   */
  override def mapAddr(f: UInt => UInt): AsyncWriteMemIO = {
    val mem = Wire(chiselTypeOf(this))
    mem.wr := wr
    waitReq := mem.waitReq
    mem.addr := f(addr)
    mem.mask := mask
    mem.din := din
    mem
  }

  /** Converts the interface to synchronous read-write. */
  def asReadWriteMemIO: ReadWriteMemIO = {
    val mem = Wire(Flipped(ReadWriteMemIO(this)))
    mem.rd := false.B
    mem.wr := wr
    waitReq := false.B
    mem.addr := addr
    mem.mask := mask
    mem.din := din
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
class AsyncReadWriteMemIO(addrWidth: Int, dataWidth: Int) extends ReadWriteMemIO(addrWidth, dataWidth) with WaitIO with ValidIO {
  def this(config: BusConfig) = this(config.addrWidth, config.dataWidth)

  /** Converts the interface to asynchronous read-only. */
  def asAsyncReadMemIO: AsyncReadMemIO = {
    val mem = Wire(Flipped(AsyncReadMemIO(this)))
    rd := mem.rd
    wr := false.B
    mem.waitReq := waitReq
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
    mem.waitReq := waitReq
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
  override def mapAddr(f: UInt => UInt): AsyncReadWriteMemIO = {
    val mem = Wire(chiselTypeOf(this))
    mem.rd := rd
    mem.wr := wr
    waitReq := mem.waitReq
    valid := mem.valid
    mem.addr := f(addr)
    mem.mask := mask
    mem.din := din
    dout := mem.dout
    mem
  }
}

object AsyncReadWriteMemIO {
  def apply(addrWidth: Int, dataWidth: Int) = new AsyncReadWriteMemIO(addrWidth, dataWidth)

  def apply(config: BusConfig) = new AsyncReadWriteMemIO(config)

  /**
   * Demultiplexes requests from a single read-write memory interface to multiple read-write memory
   * interfaces. The request is routed to the interface matching a given key.
   *
   * @param key  The key to used to select the interface.
   * @param outs A list of key-interface pairs.
   */
  def demux[K <: UInt](key: K, outs: Seq[(K, AsyncReadWriteMemIO)]): AsyncReadWriteMemIO = {
    val mem = Wire(chiselTypeOf(outs.head._2))
    outs.foreach { case (k, out) =>
      out.rd := k === key && mem.rd
      out.wr := k === key && mem.wr
      out.addr := mem.addr
      out.mask := mem.mask
      out.din := mem.din
    }
    val waitReqMap = outs.map(a => a._1 -> a._2.waitReq)
    val validMap = outs.map(a => a._1 -> a._2.valid)
    val doutMap = outs.map(a => a._1 -> a._2.dout)
    mem.waitReq := MuxLookup(key, 0.U, waitReqMap)
    mem.valid := MuxLookup(key, 0.U, validMap)
    mem.dout := MuxLookup(key, 0.U, doutMap)
    mem
  }
}