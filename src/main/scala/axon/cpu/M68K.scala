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

class M68KIO extends Bundle {
  /** Clock enable */
  val cen = Input(Bool())
  /** Address bus */
  val addr = Output(UInt(M68K.ADDR_WIDTH.W))
  /** Data input bus */
  val din = Input(Bits(M68K.DATA_WIDTH.W))
  /** Data output bus */
  val dout = Output(Bits(M68K.DATA_WIDTH.W))
  /** Address strobe */
  val as = Output(Bool())
  /** Read/write */
  val rw = Output(Bool())
  /** Upper data strobe */
  val uds = Output(Bool())
  /** Lower data strobe */
  val lds = Output(Bool())
  /** Data transfer acknowledge */
  val dtack = Input(Bool())
  /** Interrupt priority level */
  val ipl = Input(UInt(M68K.IPL_WIDTH.W))
  /** Debug port */
  val debug = new Bundle {
    val pc = Output(UInt())
    val pcw = Output(Bool())
  }
}

/** M68000 CPU */
class M68K extends Module {
  val io = IO(new M68KIO)

  class TG68 extends BlackBox {
    val io = IO(new Bundle {
      val clk = Input(Bool())
      val reset = Input(Bool())
      val clkena_in = Input(Bool())
      val data_in = Input(Bits(M68K.DATA_WIDTH.W))
      val IPL = Input(UInt(M68K.IPL_WIDTH.W))
      val dtack = Input(Bool())
      val addr = Output(UInt(M68K.ADDR_WIDTH.W))
      val data_out = Output(Bits(M68K.DATA_WIDTH.W))
      val as = Output(Bool())
      val uds = Output(Bool())
      val lds = Output(Bool())
      val rw = Output(Bool())
      val TG68_PC_o = Output(UInt(M68K.ADDR_WIDTH.W))
      val TG68_PCW_o = Output(Bool())
    })
  }

  val cpu = Module(new TG68)
  cpu.io.clk := clock.asBool
  cpu.io.reset := !reset.asBool
  cpu.io.clkena_in := io.cen
  cpu.io.data_in := io.din
  cpu.io.IPL := ~io.ipl
  cpu.io.dtack := !io.dtack
  io.addr := cpu.io.addr
  io.dout := cpu.io.data_out
  io.as := !cpu.io.as
  io.uds := !cpu.io.uds
  io.lds := !cpu.io.lds
  io.rw := cpu.io.rw
  io.debug.pc := cpu.io.TG68_PC_o
  io.debug.pcw := cpu.io.TG68_PCW_o
}

object M68K {
  /**
   * The width of the CPU address bus.
   *
   * TODO: Lies, the M68K does not have a 32-bit address bus. This needs to be addressed.
   */
  val ADDR_WIDTH = 32

  /** The width of the CPU data bus. */
  val DATA_WIDTH = 16

  /** The width of the CPU interrupt priority level value. */
  val IPL_WIDTH = 3
}
