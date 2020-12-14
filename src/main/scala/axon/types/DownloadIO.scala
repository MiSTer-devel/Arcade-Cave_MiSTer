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

package axon.types

import axon.mem.AsyncReadWriteMemIO
import chisel3._
import chisel3.util._

/** A flow control interface used to download data into the core. */
class DownloadIO private extends Bundle {
  /** Chip select */
  val cs = Input(Bool())
  /** Write enable */
  val wr = Input(Bool())
  /** Asserted when the core isn't ready to proceed with the request */
  val waitReq = Output(Bool())
  /** Index */
  val index = Input(UInt(DownloadIO.INDEX_WIDTH.W))
  /** Address bus */
  val addr = Input(UInt(DownloadIO.ADDR_WIDTH.W))
  /** Data bus */
  val dout = Input(Bits(DownloadIO.DATA_WIDTH.W))

  /** Converts the download interface to an asynchronous read-write memory interface */
  def asAsyncReadWriteMemIO: AsyncReadWriteMemIO = {
    val wire = Wire(AsyncReadWriteMemIO(DownloadIO.ADDR_WIDTH, DownloadIO.DATA_WIDTH))
    wire.rd := false.B
    wire.wr := cs && wr
    waitReq := wr && wire.waitReq
    wire.addr := addr
    wire.mask := Fill(wire.maskWidth, 1.U)
    wire.din := dout
    wire
  }
}

object DownloadIO {
  /** The width of the index */
  val INDEX_WIDTH = 8
  /** The width of the address bus */
  val ADDR_WIDTH = 27
  /** The width of the data bus */
  val DATA_WIDTH = 16

  def apply() = new DownloadIO
}
