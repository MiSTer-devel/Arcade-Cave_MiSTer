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
