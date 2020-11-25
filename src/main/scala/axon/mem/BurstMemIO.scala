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

trait BurstIO {
  /** The number of words to transfer during a burst */
  val burstCount = Output(UInt(8.W))
}

/**
 * A flow control interface for reading from bursted memory.
 *
 * @param addrWidth The width of the address bus.
 * @param dataWidth The width of the data bus.
 */
class BurstReadMemIO protected (addrWidth: Int, dataWidth: Int) extends AsyncReadMemIO(addrWidth, dataWidth) with BurstIO {
  override def cloneType: this.type = new BurstReadMemIO(addrWidth, dataWidth).asInstanceOf[this.type]
}

object BurstReadMemIO {
  def apply(addrWidth: Int, dataWidth: Int) = new BurstReadMemIO(addrWidth, dataWidth)
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
}

object BurstReadWriteMemIO {
  def apply(addrWidth: Int, dataWidth: Int) = new BurstReadWriteMemIO(addrWidth, dataWidth)
}

