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
}

object Audio {
  /**
   * Constructs an audio sample from the left and right channel values.
   *
   * @param left The left channel value.
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
