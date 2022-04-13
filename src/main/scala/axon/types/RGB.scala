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

package axon.types

import chisel3._

/**
 * Represents a RGB color.
 *
 * @param redWidth   The red channel width.
 * @param greenWidth The green channel width.
 * @param blueWidth  The blue channel width.
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
}

object RGB {
  /**
   * Constructs a RGB color from a single value.
   *
   * @param value The value of the red, green, and blue channels.
   */
  def apply(value: UInt): RGB = RGB(value, value, value)

  /**
   * Constructs a RGB color from a list.
   *
   * @param value An list containing the values of the red, green, and blue channels.
   */
  def apply(value: Seq[Bits]): RGB = RGB(value(0).asUInt, value(1).asUInt, value(2).asUInt)

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
