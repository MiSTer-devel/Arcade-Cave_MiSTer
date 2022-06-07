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

package axon.clk

import chisel3._

private class ClockDivider(d: Double, width: Int) {
  val n = ((1 << width) / d).round
  val next = Wire(UInt((width + 1).W))
  val counter = RegNext(next.tail(1))
  val clockEnable = RegNext(next.head(1).asBool)
  next := counter +& n.U

  printf(p"ClockDivider(counter: 0x${ Hexadecimal(counter) }, cen: $clockEnable )\n")
}

object ClockDivider {
  /**
   * Constructs a fractional clock divider.
   *
   * @param d     The divisor.
   * @param width The width of the counter.
   * @return A clock enable signal.
   */
  def apply(d: Double, width: Int = 16): Bool = {
    val clockDivider = new ClockDivider(d, width)
    clockDivider.clockEnable
  }
}
