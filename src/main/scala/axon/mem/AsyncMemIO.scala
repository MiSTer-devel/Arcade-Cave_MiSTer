/*
 *    __   __     __  __     __         __
 *   /\ "-.\ \   /\ \/\ \   /\ \       /\ \
 *   \ \ \-.  \  \ \ \_\ \  \ \ \____  \ \ \____
 *    \ \_\\"\_\  \ \_____\  \ \_____\  \ \_____\
 *     \/_/ \/_/   \/_____/   \/_____/   \/_____/
 *    ______     ______       __     ______     ______     ______
 *   /\  __ \   /\  == \     /\ \   /\  ___\   /\  ___\   /\__  _\
 *   \ \ \/\ \  \ \  __<    _\_\ \  \ \  __\   \ \ \____  \/_/\ \/
 *    \ \_____\  \ \_____\ /\_____\  \ \_____\  \ \_____\    \ \_\
 *     \/_____/   \/_____/ \/_____/   \/_____/   \/_____/     \/_/
 *
 *  https://joshbassett.info
 *  https://twitter.com/nullobject
 *  https://github.com/nullobject
 *
 *  Copyright (c) 2020 Josh Bassett
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
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
class AsyncReadMemIO protected (addrWidth: Int, dataWidth: Int) extends ReadMemIO(addrWidth, dataWidth) with WaitIO with ValidIO {
  override def cloneType: this.type = new AsyncReadMemIO(addrWidth, dataWidth).asInstanceOf[this.type]
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
class AsyncWriteMemIO protected (addrWidth: Int, dataWidth: Int) extends WriteMemIO(addrWidth, dataWidth) with WaitIO {
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
class AsyncReadWriteMemIO protected (addrWidth: Int, dataWidth: Int) extends ReadWriteMemIO(addrWidth, dataWidth) with WaitIO with ValidIO {
  override def cloneType: this.type = new AsyncReadWriteMemIO(addrWidth, dataWidth).asInstanceOf[this.type]

  /** Converts the interface to read-only */
  def asAsyncReadMemIO: AsyncReadMemIO = {
    val wire = Wire(Flipped(AsyncReadMemIO(addrWidth, dataWidth)))
    rd := wire.rd
    wr := false.B
    wire.waitReq := waitReq
    wire.valid := valid
    addr := wire.addr
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

  /** Converts the interface to synchronous read-only */
  override def asReadMemIO: ReadMemIO = {
    val wire = Wire(Flipped(ReadMemIO(addrWidth, dataWidth)))
    rd := wire.rd
    wr := false.B
    addr := wire.addr
    din := 0.U
    wire.dout := dout
    wire
  }
}

object AsyncReadWriteMemIO {
  def apply(addrWidth: Int, dataWidth: Int) = new AsyncReadWriteMemIO(addrWidth, dataWidth)
}