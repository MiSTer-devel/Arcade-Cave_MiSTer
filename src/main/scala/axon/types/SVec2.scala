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
 * Represents a signed 2D vector.
 *
 * @param xWidth The X data width.
 * @param yWidth The Y data width.
 */
class SVec2(xWidth: Int, yWidth: Int) extends Bundle {
  /** Horizontal position */
  val x = SInt(xWidth.W)
  /** Vertical position */
  val y = SInt(yWidth.W)

  def this(n: Int) = this(n, n)

  /** Addition operator. */
  def +(that: SVec2) = SVec2(this.x + that.x, this.y + that.y)

  /** Addition operator (expanding width). */
  def +&(that: SVec2) = SVec2(this.x +& that.x, this.y +& that.y)

  /** Subtraction operator. */
  def -(that: SVec2) = SVec2(this.x - that.x, this.y - that.y)

  /** Scalar multiplication operator. */
  def *(n: UInt) = SVec2(this.x * n, this.y * n)

  /** Static left shift operator. */
  def <<(n: Int) = SVec2(this.x << n, this.y << n)

  /** Dynamic left shift operator. */
  def <<(n: UInt) = SVec2(this.x << n, this.y << n)

  /** Static right shift operator. */
  def >>(n: Int) = SVec2(this.x >> n, this.y >> n)

  /** Dynamic right shift operator. */
  def >>(n: UInt) = SVec2(this.x >> n, this.y >> n)
}

object SVec2 {
  /**
   * Creates a signed vector from X and Y bitvector values.
   *
   * @param x The horizontal position.
   * @param y The vertical position.
   */
  def apply(x: Bits, y: Bits): SVec2 = {
    val pos = Wire(new SVec2(x.getWidth, y.getWidth))
    pos.x := x.asSInt
    pos.y := y.asSInt
    pos
  }

  /**
   * Creates a zero vector.
   *
   * @param width The data width.
   */
  def zero(width: Int) = SVec2(0.U(width.W), 0.U(width.W))
}
