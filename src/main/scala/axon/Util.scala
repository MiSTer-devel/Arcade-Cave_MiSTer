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

package axon

import chisel3._
import chisel3.util._

/** Utility functions. */
object Util {
  /**
   * Checks whether an unsigned value is in the given range.
   *
   * @param n The value to check.
   * @param r The range.
   */
  def between(n: UInt, r: Range): Bool = n >= r.start.U && n <= r.end.U

  /**
   * Checks whether an unsigned value is in the given range.
   *
   * @param n     The value to check.
   * @param start The start of the range.
   * @param end   The end of the range.
   */
  def between(n: UInt, start: UInt, end: UInt): Bool = n >= start && n <= end

  /**
   * Rotates the bits in the given value to the left.
   *
   * @param n The value to rotate.
   */
  def rotateLeft(n: UInt): UInt = {
    val w = n.getWidth
    n(w - 2, 0) ## n(w - 1)
  }

  /**
   * Rotates the bits in the given value to the right.
   *
   * @param n The value to rotate.
   */
  def rotateRight(n: UInt): UInt = {
    val w = n.getWidth
    n(0) ## n(w - 1, 1)
  }

  /**
   * Splits a bitvector value into a sequence of bitvectors.
   *
   * @param bits  The value.
   * @param n     The number of elements.
   * @param width The width of each element.
   */
  def decode(bits: Bits, n: Int, width: Int): Seq[Bits] =
    Seq.tabulate(n) { i =>
      bits(((i + 1) * width) - 1, i * width)
    }

  /**
   * Creates a bitmask with the given number of lower bits masked off (set to zero). All other bits
   * are set to one.
   *
   * @param n     The number of lower bits to be masked off.
   * @param width The width in bits.
   */
  def bitmask(n: Int, width: Int): UInt = (~((1.U(width.W) << n).asUInt - 1.U)).asUInt

  def bitmask(n: UInt, width: Int): UInt = (~((1.U(width.W) << n).asUInt - 1.U)).asUInt

  /**
   * Pads the words packed into a bitvector value.
   *
   * @param bits      The value.
   * @param n         The number of words.
   * @param fromWidth The width of the source words.
   * @param toWidth   The width of the destination words.
   * @return
   */
  def padWords(bits: Bits, n: Int, fromWidth: Int, toWidth: Int): Bits =
    Cat(Util.decode(bits, n, fromWidth).map(_.pad(toWidth)).reverse)

  /**
   * Detects edges of a signal.
   *
   * @param s The signal used to detect edges.
   */
  def edge(s: Bool): Bool = s ^ RegNext(s)

  /**
   * Detects rising edges of a signal.
   *
   * @param s The signal used to detect edges.
   */
  def rising(s: Bool): Bool = s && !RegNext(s)

  /**
   * Detects falling edges of a signal.
   *
   * @param s The signal used to detect edges.
   */
  def falling(s: Bool): Bool = !s && RegNext(s)

  /**
   * Holds a signal while the given trigger is asserted.
   *
   * @param s The signal value.
   * @param t The trigger value.
   */
  def hold[T <: Data](s: T, t: Bool): T = {
    val u = rising(t)
    val reg = RegEnable(s, u)
    Mux(t && !u, reg, s)
  }

  /**
   * Latches a signal when it is asserted.
   *
   * Once latched, the output will remain stable until the latch is reset by the clear signal.
   *
   * @param s     The signal value.
   * @param clear The clear signal.
   */
  def latch(s: Bool, clear: Bool): Bool = {
    val enableReg = RegInit(false.B)
    when(s) {
      enableReg := true.B
    }.elsewhen(clear) {
      enableReg := false.B
    }
    s || (enableReg && !clear)
  }

  /**
   * Synchronously latches and clears a signal.
   *
   * Once latched, the output will remain stable until the latch is cleared by the clear signal.
   *
   * @param s     The signal value.
   * @param clear The clear signal.
   */
  def latchSync(s: Bool, clear: Bool): Bool = {
    val enableReg = RegInit(false.B)
    when(clear) {
      enableReg := false.B
    }.elsewhen(s) {
      enableReg := true.B
    }
    enableReg
  }

  /**
   * Latches a signal when the given trigger is asserted.
   *
   * The output will remain stable until the latch is triggered or cleared.
   *
   * @param s     The signal value.
   * @param t     The trigger value.
   * @param clear The clear signal.
   */
  def latch[T <: Data](s: T, t: Bool, clear: Bool): T = {
    val dataReg = RegEnable(s, t)
    val enableReg = RegInit(false.B)
    when(t) {
      enableReg := true.B
    }.elsewhen(clear) {
      enableReg := false.B
    }
    Mux(enableReg && !clear, dataReg, s)
  }

  /**
   * Toggles a bit while the enable signal is asserted.
   *
   * @param enable The enable signal.
   */
  def toggle(enable: Bool = true.B): Bool = {
    val dataReg = RegInit(false.B)
    when(enable) { dataReg := !dataReg }
    dataReg
  }

  /**
   * Generates a sync pulse for rising edges of the target clock.
   *
   * @param targetClock The target clock domain.
   */
  def sync(targetClock: Clock): Bool = {
    val s = withClock(targetClock) { toggle() }
    edge(s)
  }

  /**
   * Clamps a given number between two values.
   *
   * @param n The number to be clamped.
   * @param a The minimum value.
   * @param b The maximum value.
   */
  def clamp(n: SInt, a: Int, b: Int): SInt = n.max(a.S).min(b.S)
}
