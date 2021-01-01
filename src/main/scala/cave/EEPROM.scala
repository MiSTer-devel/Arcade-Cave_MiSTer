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
 * Copyright (c) 2021 Josh Bassett
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

package cave

import axon.cpu.m68k.CPU
import axon.mem.WriteMemIO
import chisel3._
import chisel3.util._

/**
 * The 93C06/46 EEPROM is used to store configuration data for the game.
 *
 * This module wraps the 3-wire serial interface with a write-only memory interface that can be
 * accessed by the CPU.
 *
 * Internally, this module just wraps Jotego's jt9346 module.
 */
class EEPROM extends Module {
  val io = IO(new Bundle {
    /** Memory port */
    val mem = Flipped(WriteMemIO(CPU.ADDR_WIDTH, CPU.DATA_WIDTH))
    /** Output data */
    val dout = Output(Bool())
  })

  class EEPROMBlackBox extends BlackBox {
    val io = IO(new Bundle {
      val clk = Input(Clock())
      val rst = Input(Reset())
      val sclk = Input(Bool())
      val sdi = Input(Bool())
      val sdo = Output(Bool())
      val scs = Input(Bool())
    })

    override def desiredName = "jt9346"
  }

  val eeprom = Module(new EEPROMBlackBox)
  eeprom.io.clk := clock
  eeprom.io.rst := reset
  eeprom.io.scs := RegEnable(io.mem.din(9), false.B, io.mem.wr)
  eeprom.io.sclk := RegEnable(io.mem.din(10), false.B, io.mem.wr)
  eeprom.io.sdi := RegEnable(io.mem.din(11), false.B, io.mem.wr)
  io.dout := eeprom.io.sdo
}
