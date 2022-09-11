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

package arcadia.snd

import arcadia.clk.ClockDivider
import chisel3._
import chisel3.util._

/**
 * The 5205 is a ADPCM sound chip.
 *
 * @param clockFreq  The system clock frequency (Hz).
 * @param sampleFreq The sample clock frequency (Hz).
 * @note This module wraps jotego's JT5205 implementation.
 * @see https://github.com/jotego/jt5205
 */
class JT5205(clockFreq: Double, sampleFreq: Double) extends Module {
  val io = IO(new Bundle {
    /** Data input port */
    val din = Input(Bits(4.W))
    /** Audio output port */
    val audio = ValidIO(SInt(12.W))
    /** Sample clock */
    val vclk = Output(Bool())
  })

  class JT5205_ extends BlackBox {
    val io = IO(new Bundle {
      val rst = Input(Bool())
      val clk = Input(Bool())
      val cen = Input(Bool())
      val sel = Input(Bits(2.W))
      val din = Input(Bits(4.W))
      val sound = Output(SInt(12.W))
      val sample = Output(Bool())
      val vclk_o = Output(Bool())
    })

    override def desiredName = "jt5205"
  }

  val m = Module(new JT5205_)
  m.io.clk := clock.asBool
  m.io.rst := reset.asBool
  m.io.cen := ClockDivider(clockFreq / sampleFreq)
  m.io.sel := "b10".U // 8 kHz
  m.io.din := io.din
  io.audio.valid := m.io.sample
  io.audio.bits := m.io.sound
  io.vclk := m.io.vclk_o
}
