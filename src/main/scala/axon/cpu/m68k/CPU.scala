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

package axon.cpu.m68k

import chisel3._

/** An interface for the M68000 CPU. */
class CPUIO extends Bundle {
  /** Clock enable */
  val clockEnable = Input(Bool())
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
class CPU extends Module {
  val io = IO(new CPUIO)

  class J68 extends BlackBox(Map(
    "USE_CLK_ENA" -> 1
  )) {
    val io = IO(new Bundle {
      val rst = Input(Bool())
      val clk = Input(Bool())
      val clk_ena = Input(Bool())
      val rd_ena = Output(Bool())
      val wr_ena = Output(Bool())
      val data_ack = Input(Bool())
      val byte_ena = Output(Bits(2.W))
      val address = Output(UInt(32.W))
      val rd_data = Input(Bits(16.W))
      val wr_data = Output(Bits(16.W))
      val fc = Output(UInt(3.W))
      val ipl_n = Input(UInt(3.W))
    })

    override def desiredName = "cpu_j68"
  }

  val as = RegInit(false.B)

  // CPU
  val cpu = Module(new J68)
  cpu.io.rst := reset.asBool
  cpu.io.clk := clock.asBool
  cpu.io.clk_ena := io.clockEnable && !io.halt
  cpu.io.data_ack := io.dtack
  io.addr := cpu.io.address(23, 1)
  cpu.io.rd_data := io.din
  io.dout := cpu.io.wr_data
  io.fc := cpu.io.fc
  cpu.io.ipl_n := ~io.ipl

  // Toggle address strobe
  when(cpu.io.rd_ena | cpu.io.wr_ena) {
    as := true.B
  }.elsewhen(io.dtack) {
    as := false.B
  }

  // Outputs
  io.as := as
  io.rw := !cpu.io.wr_ena
  io.uds := cpu.io.byte_ena(1)
  io.lds := cpu.io.byte_ena(0)
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
