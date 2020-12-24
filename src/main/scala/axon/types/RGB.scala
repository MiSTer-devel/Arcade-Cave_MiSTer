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

package axon.types

import chisel3._

/**
 * Represents a RGB color. 
 *
 * @param redWidth The red channel width.
 * @param greenWidth The green channel width.
 * @param blueWidth The blue channel width.
 */
class RGB(redWidth: Int, greenWidth: Int, blueWidth: Int) extends Bundle {
  /** Red */
  val r = UInt(redWidth.W)
  /** Green */
  val g = UInt(greenWidth.W)
  /** Blue */
  val b = UInt(blueWidth.W)

  def this(n: Int) = this(n, n, n)

  /** Bitwise AND operator. */
  def &(that: RGB): RGB = {
    RGB(this.r & that.r, this.g & that.g, this.b & that.b)
  }

  /** Bitwise OR operator. */
  def |(that: RGB): RGB = {
    RGB(this.r | that.r, this.g | that.g, this.b | that.b)
  }

  /** Bitwise XOR operator. */
  def ^(that: RGB): RGB = {
    RGB(this.r ^ that.r, this.g ^ that.g, this.b ^ that.b)
  }

  override def cloneType: this.type = new RGB(redWidth, greenWidth, blueWidth).asInstanceOf[this.type]
}

object RGB {
  /**
   * Constructs a RGB color from a single value.
   *
   * @param value The value of the red, green, and blue channels.
   */
  def apply(value: UInt): RGB = {
    val rgb = Wire(new RGB(value.getWidth))
    rgb.r := value
    rgb.g := value
    rgb.b := value
    rgb
  }

  /**
   * Constructs a RGB color from red, green, and blue values.
   *
   * @param r The red channel value.
   * @param g The green channel value.
   * @param b The blue channel value.
   */
  def apply(r: UInt, g: UInt, b: UInt): RGB = {
    val rgb = Wire(new RGB(r.getWidth, g.getWidth, b.getWidth))
    rgb.r := r
    rgb.g := g
    rgb.b := b
    rgb
  }
}
