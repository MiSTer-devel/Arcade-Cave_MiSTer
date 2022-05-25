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

package axon

import axon.util.Counter
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
  def between(n: UInt, r: Range): Bool = between(n, r.start.U, r.last.U)

  /**
   * Checks whether a signed value is in the given range.
   *
   * @param n The value to check.
   * @param r The range.
   */
  def between(n: SInt, r: Range): Bool = between(n, r.start.S, r.last.S)

  /**
   * Checks whether an unsigned value is in the given range.
   *
   * @param n     The value to check.
   * @param start The start of the range.
   * @param end   The end of the range.
   */
  def between(n: UInt, start: UInt, end: UInt): Bool = n >= start && n <= end

  /**
   * Checks whether a signed value is in the given range.
   *
   * @param n     The value to check.
   * @param start The start of the range.
   * @param end   The end of the range.
   */
  def between(n: SInt, start: SInt, end: SInt): Bool = n >= start && n <= end

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
  def latch(s: Bool, clear: Bool = false.B): Bool = {
    val enableReg = RegInit(false.B)
    when(s) { enableReg := true.B }.elsewhen(clear) { enableReg := false.B }
    s || (enableReg && !clear)
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
  def latchData[T <: Data](s: T, t: Bool, clear: Bool): T = {
    val dataReg = RegEnable(s, t)
    val enableReg = RegInit(false.B)
    when(clear) { enableReg := false.B }.elsewhen(t) { enableReg := true.B }
    Mux(enableReg && !clear, dataReg, s)
  }

  /**
   * Synchronously latches and clears a signal.
   *
   * Once latched, the output will remain stable until the latch is cleared by the clear signal.
   *
   * @param s     The signal value.
   * @param clear The clear signal.
   */
  def latchSync(s: Bool, clear: Bool = false.B): Bool = {
    val enableReg = RegInit(false.B)
    when(clear) { enableReg := false.B }.elsewhen(s) { enableReg := true.B }
    enableReg
  }

  /**
   * Synchronously triggers a pulse of the given width.
   *
   * @param n The pulse width.
   * @param t The trigger value.
   */
  def pulseSync(n: Int, t: Bool): Bool = {
    val s = Wire(Bool())
    val (_, pulseCounterWrap) = Counter.static(n, s)
    s := latchSync(rising(t), pulseCounterWrap)
    s
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
