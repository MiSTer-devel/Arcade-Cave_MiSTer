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

package arcadia.cpu.z80

import chisel3._

/** An interface for the Z80 CPU. */
class CPUIO extends Bundle {
  /** Address bus */
  val addr = Output(UInt(CPU.ADDR_WIDTH.W))
  /** Data input */
  val din = Input(Bits(CPU.DATA_WIDTH.W))
  /** Data output */
  val dout = Output(Bits(CPU.DATA_WIDTH.W))
  /** Read enable */
  val rd = Output(Bool())
  /** Write enable */
  val wr = Output(Bool())
  /** Asserted during a memory refresh */
  val rfsh = Output(Bool())
  /** Asserted during a memory request */
  val mreq = Output(Bool())
  /** Asserted during an IO request */
  val iorq = Output(Bool())
  /** Interrupt request */
  val int = Input(Bool())
  /** Non-maskable interrupt */
  val nmi = Input(Bool())
  /** Asserted during the M1 cycle */
  val m1 = Output(Bool())
  /** Asserted when the CPU has halted */
  val halt = Output(Bool())
  /** Asserted during a bus acknowledge */
  val busak = Output(Bool())
  /** Register file output (for debugging) */
  val regs = Output(new RegisterFile)
}

/** Z80 CPU */
class CPU extends Module {
  val io = IO(new CPUIO)

  /** Wraps the T80s implementation of the Z80 CPU. */
  class T80s extends BlackBox {
    val io = IO(new Bundle {
      val RESET_n = Input(Reset())
      val CLK = Input(Clock())
      val CEN = Input(Bool())
      val WAIT_n = Input(Bool())
      val INT_n = Input(Bool())
      val NMI_n = Input(Bool())
      val BUSRQ_n = Input(Bool())
      val M1_n = Output(Bool())
      val MREQ_n = Output(Bool())
      val IORQ_n = Output(Bool())
      val RD_n = Output(Bool())
      val WR_n = Output(Bool())
      val RFSH_n = Output(Bool())
      val HALT_n = Output(Bool())
      val BUSAK_n = Output(Bool())
      val A = Output(UInt(CPU.ADDR_WIDTH.W))
      val DI = Input(Bits(CPU.DATA_WIDTH.W))
      val DO = Output(Bits(CPU.DATA_WIDTH.W))
      val REG = Output(Bits(211.W))
    })
  }

  // Z80
  val cpu = Module(new T80s)
  cpu.io.RESET_n := !reset.asBool()
  cpu.io.CLK := clock
  cpu.io.CEN := true.B
  cpu.io.WAIT_n := true.B
  cpu.io.INT_n := !io.int
  cpu.io.NMI_n := !io.nmi
  cpu.io.BUSRQ_n := true.B
  cpu.io.DI := io.din
  io.mreq := !cpu.io.MREQ_n
  io.iorq := !cpu.io.IORQ_n
  io.rd := !cpu.io.RD_n
  io.wr := !cpu.io.WR_n
  io.rfsh := !cpu.io.RFSH_n
  io.halt := !cpu.io.HALT_n
  io.busak := !cpu.io.BUSAK_n
  io.m1 := !cpu.io.M1_n
  io.addr := cpu.io.A
  io.dout := cpu.io.DO
  io.regs := cpu.io.REG.asTypeOf(new RegisterFile)
}

object CPU {
  /** The width of the CPU address bus. */
  val ADDR_WIDTH = 16

  /** The width of the CPU data bus. */
  val DATA_WIDTH = 8
}
