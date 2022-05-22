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
import chisel3.internal.firrtl.Width

/**
 * Represents a RGB color.
 *
 * @param redWidth   The red channel width.
 * @param greenWidth The green channel width.
 * @param blueWidth  The blue channel width.
 */
class RGB(redWidth: Width, greenWidth: Width, blueWidth: Width) extends Bundle {
  /** Red channel */
  val r = UInt(redWidth)
  /** Green channel */
  val g = UInt(greenWidth)
  /** Blue channel */
  val b = UInt(blueWidth)

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
   * Creates a RGB bundle.
   *
   * @param width The channel width.
   * @return A bundle.
   */
  def apply(width: Width): RGB = new RGB(width, width, width)

  /**
   * Constructs a RGB color from a single value.
   *
   * @param value The value of the red, green, and blue channels.
   * @return A RGB color.
   */
  def apply(value: Bits): RGB = RGB(value, value, value)

  /**
   * Constructs a RGB color from a list.
   *
   * @param value An list containing the values of the red, green, and blue channels.
   * @return A RGB color.
   */
  def apply(value: Seq[Bits]): RGB = RGB(value(0), value(1), value(2))

  /**
   * Constructs a RGB color from red, green, and blue values.
   *
   * @param r The red channel value.
   * @param g The green channel value.
   * @param b The blue channel value.
   * @return A RGB color.
   */
  def apply(r: Bits, g: Bits, b: Bits): RGB = {
    val rgb = Wire(new RGB(r.getWidth.W, g.getWidth.W, b.getWidth.W))
    rgb.r := r
    rgb.g := g
    rgb.b := b
    rgb
  }

  /**
   * Creates a zero RGB color.
   *
   * @return A RGB color.
   */
  def zero = RGB(0.U, 0.U, 0.U)
}
