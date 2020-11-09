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

package axon.cpu

import chisel3._

/** M68000 CPU */
class M68K extends Module {
  /** The width of the CPU address bus. */
  val ADDR_WIDTH = 23

  /** The width of the CPU data bus. */
  val DATA_WIDTH = 16

  val io = IO(new Bundle {
    /** address bus */
    val addr = Output(UInt(ADDR_WIDTH.W))

    /** data input */
    val din = Input(Bits(DATA_WIDTH.W))

    /** data output */
    val dout = Output(Bits(DATA_WIDTH.W))
  })

  class FX68K extends BlackBox {
    val io = IO(new Bundle {
      val rst = Input(Reset())
      val clk = Input(Clock())
      val eab = Output(UInt(ADDR_WIDTH.W))
      val iEdb = Input(Bits(DATA_WIDTH.W))
      val oEdb = Output(Bits(DATA_WIDTH.W))
    })
  }

  val fx68k = Module(new FX68K)
  fx68k.io.rst := reset
  fx68k.io.clk := clock
  io.addr := fx68k.io.eab
  io.dout := fx68k.io.oEdb
  fx68k.io.iEdb := io.din
}
