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

trait WaitIO {
  /** The wait request signal is asserted when the device isn't ready to proceed with the request */
  val waitReq = Input(Bool())
}

trait ValidIO {
  /** The valid signal is asserted when the output data is valid */
  val valid = Input(Bool())
}

/**
 * A flow control interface for reading from asynchronous memory.
 *
 * @param addrWidth The width of the address bus.
 * @param dataWidth The width of the data bus.
 */
class AsyncReadMemIO protected(addrWidth: Int, dataWidth: Int) extends ReadMemIO(addrWidth, dataWidth) with WaitIO with ValidIO {
  override def cloneType: this.type = new AsyncReadMemIO(addrWidth, dataWidth).asInstanceOf[this.type]

  /**
   * Maps the address using the given function.
   *
   * @param f The transform function.
   */
  override def mapAddr(f: UInt => UInt): AsyncReadMemIO = {
    val mem = Wire(chiselTypeOf(this))
    mem.rd := this.rd
    this.valid := mem.valid
    mem.addr := f(this.addr)
    this.dout := mem.dout
    mem
  }
}

object AsyncReadMemIO {
  def apply(addrWidth: Int, dataWidth: Int) = new AsyncReadMemIO(addrWidth, dataWidth)
}

/**
 * A control interface for writing to asynchronous memory.
 *
 * @param addrWidth The width of the address bus.
 * @param dataWidth The width of the data bus.
 */
class AsyncWriteMemIO protected(addrWidth: Int, dataWidth: Int) extends WriteMemIO(addrWidth, dataWidth) with WaitIO {
  override def cloneType: this.type = new AsyncWriteMemIO(addrWidth, dataWidth).asInstanceOf[this.type]
}

object AsyncWriteMemIO {
  def apply(addrWidth: Int, dataWidth: Int) = new AsyncWriteMemIO(addrWidth, dataWidth)
}

/**
 * A flow control interface for reading and writing to asynchronous memory.
 *
 * @param addrWidth The width of the address bus.
 * @param dataWidth The width of the data bus.
 */
class AsyncReadWriteMemIO protected(addrWidth: Int, dataWidth: Int) extends ReadWriteMemIO(addrWidth, dataWidth) with WaitIO with ValidIO {
  override def cloneType: this.type = new AsyncReadWriteMemIO(addrWidth, dataWidth).asInstanceOf[this.type]

  /** Converts the interface to read-only */
  def asAsyncReadMemIO: AsyncReadMemIO = {
    val wire = Wire(Flipped(AsyncReadMemIO(addrWidth, dataWidth)))
    rd := wire.rd
    wr := false.B
    wire.waitReq := waitReq
    wire.valid := valid
    addr := wire.addr
    mask := 0.U
    din := 0.U
    wire.dout := dout
    wire
  }

  /** Converts the interface to write-only */
  def asAsyncWriteMemIO: AsyncWriteMemIO = {
    val wire = Wire(Flipped(AsyncWriteMemIO(addrWidth, dataWidth)))
    rd := false.B
    wr := wire.wr
    wire.waitReq := waitReq
    addr := wire.addr
    din := wire.din
    wire
  }
}

object AsyncReadWriteMemIO {
  def apply(addrWidth: Int, dataWidth: Int) = new AsyncReadWriteMemIO(addrWidth, dataWidth)
}