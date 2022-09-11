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
 * Copyright (c) 2022 Josh Bassett
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

package arcadia.cpu.m68k

import arcadia.util.Counter
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
}

/** M68000 CPU */
class CPU(clockDiv: Int = 1) extends Module {
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
      val eab = Output(UInt(CPU.ADDR_WIDTH.W))
      // Data bus
      val iEdb = Input(Bits(CPU.DATA_WIDTH.W))
      val oEdb = Output(Bits(CPU.DATA_WIDTH.W))
    })

    override def desiredName = "fx68k"
  }

  // The FX68K module requires two clock enable signals (PHI1 and PHI2) to enable the rising and
  // falling edges of the clock
  val (_, phi1) = Counter.static(clockDiv)
  val phi2 = ShiftRegister(phi1, clockDiv / 2)

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
  cpu.io.DTACKn := !io.dtack
  cpu.io.BERRn := true.B
  cpu.io.BRn := true.B
  cpu.io.BGACKn := true.B
  cpu.io.VPAn := !io.vpa
  cpu.io.IPL0n := !io.ipl(0)
  cpu.io.IPL1n := !io.ipl(1)
  cpu.io.IPL2n := !io.ipl(2)
  io.fc := Cat(cpu.io.FC2, cpu.io.FC1, cpu.io.FC0)
  io.addr := cpu.io.eab
  cpu.io.iEdb := io.din
  io.dout := cpu.io.oEdb
}

object CPU {
  /** The width of the CPU address bus. */
  val ADDR_WIDTH = 23
  /** The width of the CPU data bus. */
  val DATA_WIDTH = 16
  /** The width of the CPU interrupt priority level value. */
  val IPL_WIDTH = 3
  /** The width of the function code value. */
  val FC_WIDTH = 3
}
