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

package axon.snd

import axon.Util
import chisel3._

/** Represents a stereo pair of audio samples. */
class Audio(private val n: Int) extends Bundle {
  /** Left channel data */
  val left = SInt(n.W)
  /** Right channel data */
  val right = SInt(n.W)

  /**
   * Adds the given audio sample.
   *
   * @param that The audio sample.
   */
  def +(that: Audio): Audio = Audio(this.left + that.left, this.right + that.right)(n)

  /**
   * Clamps the sample between two given values.
   *
   * @param a The minimum value.
   * @param b The maximum value.
   */
  def clamp(a: Int, b: Int): Audio = Audio(Util.clamp(left, a, b), Util.clamp(right, a, b))(n)

  /**
   * Resizes the width of the sample.
   *
   * The sample will be truncated to the new width.
   *
   * @param m The new sample width.
   */
  def resize(m: Int): Audio = {
    assert(m < n, "The new sample width must be smaller than the original")
    Audio(left.head(m).asSInt, right.head(m).asSInt)(m)
  }
}

object Audio {
  /**
   * Constructs an audio sample from the left and right channel values.
   *
   * @param left  The left channel value.
   * @param right The right channel value.
   */
  def apply(left: SInt, right: SInt)(n: Int): Audio = {
    val sample = Wire(new Audio(n))
    sample.left := left
    sample.right := right
    sample
  }

  /** Constructs an audio sample with zero left and right channel values. */
  def zero(n: Int) = Audio(0.S, 0.S)(n)
}
