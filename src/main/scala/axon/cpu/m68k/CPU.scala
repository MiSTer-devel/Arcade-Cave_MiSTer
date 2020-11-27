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

package axon.cpu.m68k

import axon.Util
import chisel3._
import chisel3.util._

/** An interface for the M68000 CPU. */
class CPUIO extends Bundle {
  /** Halt */
  val halt = Input(Bool())
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
  /** Valid peripheral address */
  val vpa = Input(Bool())
  /** Interrupt priority level */
  val ipl = Input(UInt(CPU.IPL_WIDTH.W))
  /** Function code */
  val fc = Output(UInt(CPU.FC_WIDTH.W))
  /** Address bus */
  val addr = Output(UInt(CPU.ADDR_WIDTH.W))
  /** Data input bus */
  val din = Input(Bits(CPU.DATA_WIDTH.W))
  /** Data output bus */
  val dout = Output(Bits(CPU.DATA_WIDTH.W))
  /** Debug port */
  val debug = new Bundle {
    val pc = Output(UInt())
    val pcw = Output(Bool())
  }
}

/** M68000 CPU */
class CPU extends Module {
  /**
   * Create a memory map for the given address.
   *
   * @param a The address.
   */
  def memMap(a: Int) = new MemMap(io, Range(a, a))

  /**
   * Create a memory map for the given address range.
   *
   * @param r The address range.
   */
  def memMap(r: Range) = new MemMap(io, r)

  val io = IO(new CPUIO)

  class FX68K extends BlackBox {
    val io = IO(new Bundle {
      // System control
      val clk = Input(Bool())
      val enPhi1 = Input(Bool())
      val enPhi2 = Input(Bool())
      val extReset = Input(Bool())
      val pwrUp = Input(Bool())
      val HALTn = Input(Bool())
      // Asynchronous bus control
      val ASn = Output(Bool())
      val eRWn = Output(Bool())
      val UDSn = Output(Bool())
      val LDSn = Output(Bool())
      val DTACKn = Input(Bool())
      val BERRn = Input(Bool())
      // Synchronous bus control
      val E = Output(Bool())
      val VPAn = Input(Bool())
      val VMAn = Output(Bool())
      // Bus arbitration control
      val BRn = Input(Bool())
      val BGn = Output(Bool())
      val BGACKn = Input(Bool())
      // Interrupt control
      val IPL0n = Input(Bool())
      val IPL1n = Input(Bool())
      val IPL2n = Input(Bool())
      // Function code
      val FC0 = Output(Bool())
      val FC1 = Output(Bool())
      val FC2 = Output(Bool())
      // Address bus
      val eab = Output(UInt(23.W))
      // Data bus
      val iEdb = Input(Bits(16.W))
      val oEdb = Output(Bits(16.W))
    })

    override def desiredName = "fx68k"
  }

  // Registers
  val dinReg = RegInit(0.U(CPU.DATA_WIDTH.W))
  val dtackReg = RegInit(false.B)

  // Clock enable signals
  //
  // The FX68K module requires an input clock that is twice the frequency of the desired clock speed. It has two clock
  // enable signals (PHI1 and PHI2) that trigger the rising and falling edges of the CPU clock.
  //
  // To generate the PHI1 and PHI2 clock enable signals, we toggle a bit and use the normal and inverted values.
  val phi1 = Util.toggle()
  val phi2 = !phi1

  // CPU
  val cpu = Module(new FX68K)
  cpu.io.clk := clock.asBool
  cpu.io.enPhi1 := phi1
  cpu.io.enPhi2 := phi2
  cpu.io.extReset := reset.asBool
  cpu.io.pwrUp := reset.asBool
  cpu.io.HALTn := !io.halt
  io.as := !cpu.io.ASn
  io.rw := cpu.io.eRWn
  io.uds := !cpu.io.UDSn
  io.lds := !cpu.io.LDSn
  cpu.io.DTACKn := !dtackReg
  cpu.io.BERRn := true.B
  cpu.io.BRn := true.B
  cpu.io.BGACKn := true.B
  cpu.io.VPAn := !io.vpa
  cpu.io.IPL0n := !io.ipl(0)
  cpu.io.IPL1n := !io.ipl(1)
  cpu.io.IPL2n := !io.ipl(2)
  io.fc := Cat(cpu.io.FC2, cpu.io.FC1, cpu.io.FC0)
  io.addr := cpu.io.eab ## 0.U
  cpu.io.iEdb := dinReg
  io.dout := cpu.io.oEdb
  io.debug.pc := 0.U
  io.debug.pcw := 0.U

  // FIXME: This shouldn't be in the CPU module
  when(io.dtack) {
    dinReg := io.din
    dtackReg := true.B
  }.elsewhen(cpu.io.ASn) {
    dtackReg := false.B
  }
}

object CPU {
  /** The width of the CPU address bus. */
  val ADDR_WIDTH = 24

  /** The width of the CPU data bus. */
  val DATA_WIDTH = 16

  /** The width of the CPU interrupt priority level value. */
  val IPL_WIDTH = 3

  /** The width of the function code value. */
  val FC_WIDTH = 3
}
