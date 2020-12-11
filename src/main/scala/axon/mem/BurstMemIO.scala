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
import chisel3.util._

trait BurstIO {
  /** The number of words to transfer during a burst */
  val burstLength = Output(UInt(8.W))
  /** A flag indicating whether the burst has finished */
  val burstDone = Input(Bool())
}

/**
 * A flow control interface for reading from bursted memory.
 *
 * @param addrWidth The width of the address bus.
 * @param dataWidth The width of the data bus.
 */
class BurstReadMemIO protected (addrWidth: Int, dataWidth: Int) extends AsyncReadMemIO(addrWidth, dataWidth) with BurstIO {
  override def cloneType: this.type = new BurstReadMemIO(addrWidth, dataWidth).asInstanceOf[this.type]

  /**
   * Maps the address using the given function.
   *
   * @param f The transform function.
   */
  override def mapAddr(f: UInt => UInt): BurstReadMemIO = {
    val mem = Wire(chiselTypeOf(this))
    mem.rd := this.rd
    mem.burstLength := this.burstLength
    mem.addr := f(this.addr)
    this.burstDone := mem.burstDone
    this.waitReq := mem.waitReq
    this.valid := mem.valid
    this.dout := mem.dout
    mem
  }
}

object BurstReadMemIO {
  def apply(addrWidth: Int, dataWidth: Int) = new BurstReadMemIO(addrWidth, dataWidth)

  /**
   * Multiplexes requests from multiple write-only memory interface to a single write-only memory interfaces. The
   * request is routed to the first enabled interface.
   *
   * @param outs A list of enable-interface pairs.
   */
  def mux(outs: Seq[(Bool, BurstReadMemIO)]): BurstReadMemIO = {
    val mem = Wire(chiselTypeOf(outs.head._2))
    mem.burstLength := MuxCase(0.U, outs.map(a => a._1 -> a._2.burstLength))
    mem.rd := MuxCase(false.B, outs.map(a => a._1 -> a._2.rd))
    mem.addr := MuxCase(DontCare, outs.map(a => a._1 -> a._2.addr))
    outs.foreach { case (selected, out) =>
      out.burstDone := mem.burstDone && selected
      out.waitReq := mem.waitReq || !selected
      out.valid := mem.valid && selected
      out.dout := mem.dout
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
class BurstWriteMemIO protected (addrWidth: Int, dataWidth: Int) extends AsyncWriteMemIO(addrWidth, dataWidth) with BurstIO {
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
class BurstReadWriteMemIO protected (addrWidth: Int, dataWidth: Int) extends AsyncReadWriteMemIO(addrWidth, dataWidth) with BurstIO {
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
}

