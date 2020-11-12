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

/**
 * A set of registers.
 *
 * @param numRegs The number of registers in the register file.
 */
class RegisterFile(numRegs: Int) extends Module {
  /** The width of the address bus. */
  val ADDR_WIDTH = log2Up(numRegs)
  /** The width of the data bus. */
  val DATA_WIDTH = 16

  val io = IO(new Bundle {
    /** Read-write port */
    val mem = Flipped(ReadWriteMemIO(ADDR_WIDTH, DATA_WIDTH))
    /** The register file */
    val regs = Output(Vec(numRegs, Bits(DATA_WIDTH.W)))
  })

  // Data registers
  val dataRegs = Reg(Vec(numRegs, Bits(DATA_WIDTH.W)))

  // Alias the current data register
  val dataReg = dataRegs(io.mem.addr)

  // Split data register into a vector of bytes
  val bytes = dataReg.asTypeOf(Vec(io.mem.maskWidth, Bits(8.W)))

  // Write masked bytes to the data register
  0.until(io.mem.maskWidth).foreach { n =>
    when(io.mem.wr && io.mem.mask(n)) { bytes(n) := io.mem.din((n+1)*8-1, n*8) }
  }

  // Concatenate the bytes and update the current data register
  dataReg := bytes.asUInt

  // Outputs
  io.mem.dout := dataReg
  io.regs := dataRegs
}
