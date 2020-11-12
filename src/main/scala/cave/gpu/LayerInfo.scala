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

package cave.gpu

import axon.mem.{ReadMemIO, ReadWriteMemIO}
import chisel3._
import chisel3.util._

/** A set of registers that describe the properties of a layer. */
class LayerInfo extends Module {
  /** The number of layer registers. */
  val NUM_REGS = 3
  /** The width of the port A address bus. */
  val ADDR_WIDTH_A = 2
  /** The width of the port A data bus. */
  val DATA_WIDTH_A = 16
  /** The width of the port B address bus. */
  val ADDR_WIDTH_B = 1
  /** The width of the port B data bus. */
  val DATA_WIDTH_B = 48

  val io = IO(new Bundle {
    /** Clock B */
    val clockB = Input(Clock())
    /** Read-write port */
    val portA = Flipped(ReadWriteMemIO(ADDR_WIDTH_A, DATA_WIDTH_A))
    /** Read-only port */
    val portB = Flipped(ReadMemIO(ADDR_WIDTH_B, DATA_WIDTH_B))
  })

  // Registers
  val dataRegs = Reg(Vec(NUM_REGS, Bits(DATA_WIDTH_A.W)))
  val outReg = withClock(io.clockB) { RegEnable(dataRegs.asUInt, io.portB.rd) }

  // Alias the current data register
  val dataReg = dataRegs(io.portA.addr)

  // Split data register into a vector of bytes
  val bytes = dataReg.asTypeOf(Vec(io.portA.maskWidth, Bits(8.W)))

  // Write masked bytes to the data register
  0.until(io.portA.maskWidth).foreach { n =>
    when(io.portA.wr && io.portA.mask(n)) { bytes(n) := io.portA.din((n+1)*8-1, n*8) }
  }

  // Concatenate the bytes and update the current data register
  dataReg := bytes.asUInt

  // Outputs
  io.portA.dout := dataReg
  io.portB.dout := outReg
}
