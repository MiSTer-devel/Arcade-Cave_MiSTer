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

package arcadia.util

import chisel3._
import chisel3.util._

private class CounterStatic(to: Long, init: Long) {
  require(to >= init, s"Counter value must greater than initial value, got: $to")
  val value = if (to > 1) RegInit(init.U(log2Ceil(to).W)) else init.U

  /** Increments the counter */
  def inc(): Bool = {
    if (to > 1) {
      val wrap = value === (to - 1).U
      value := value + 1.U
      if (!isPow2(to)) {
        when(wrap) { value := 0.U }
      }
      wrap
    } else {
      true.B
    }
  }

  /** Resets the counter to its initial value */
  def reset(): Unit = {
    if (to > 1) {
      value := init.U
    }
  }
}

private class CounterDynamic(to: UInt, init: Long) {
  val value = RegInit(init.U(to.getWidth.W))

  /** Increments the counter */
  def inc(): Bool = {
    val wrap = value === to - 1.U || to === 0.U
    value := value + 1.U
    when(wrap) { value := 0.U }
    wrap
  }

  /** Resets the counter to its initial value */
  def reset(): Unit = {
    value := init.U
  }
}

object Counter {
  def static(n: Long, enable: Bool = true.B, reset: Bool = false.B, init: Long = 0): (UInt, Bool) = {
    assert(init == 0 || init < n, "Initial value must be less than the maximum")
    val c = new CounterStatic(n, init)
    val wrap = WireInit(false.B)
    when(reset) { c.reset() }.elsewhen(enable) { wrap := c.inc() }
    (c.value, wrap)
  }

  def dynamic(n: UInt, enable: Bool = true.B, reset: Bool = false.B, init: Long = 0): (UInt, Bool) = {
    val c = new CounterDynamic(n, init)
    val wrap = WireInit(false.B)
    when(reset) { c.reset() }.elsewhen(enable) { wrap := c.inc() }
    (c.value, wrap)
  }
}
