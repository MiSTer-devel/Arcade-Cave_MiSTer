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

package axon.util

import chisel3._
import chisel3.util._

private class CounterStatic(to: Int, init: Int = 0) {
  require(to >= init, s"Counter value must greater than initial value, got: $to")
  val value = if (to > 1) RegInit(init.U(log2Ceil(to).W)) else init.U

  def inc(): Bool = {
    if (to > 1) {
      val wrap = value === (to-1).U
      value := value + 1.U
      if (!isPow2(to)) {
        when(wrap) { value := 0.U }
      }
      wrap
    } else {
      true.B
    }
  }
}

private class CounterDynamic(to: UInt) {
  val value = RegInit(0.U(to.getWidth.W))

  def inc(): Bool = {
    val wrap = value === to - 1.U
    value := value + 1.U
    when(wrap) { value := 0.U }
    wrap
  }
}

object Counter {
  def apply(cond: Bool, to: Int, init: Int = 0): (UInt, Bool) = {
    val c = new CounterStatic(to, init)
    val wrap = WireInit(false.B)
    when(cond) { wrap := c.inc() }
    (c.value, wrap)
  }

  def apply(cond: Bool, to: UInt): (UInt, Bool) = {
    val c = new CounterDynamic(to)
    val wrap = WireInit(false.B)
    when(cond) { wrap := c.inc() }
    (c.value, wrap)
  }
}