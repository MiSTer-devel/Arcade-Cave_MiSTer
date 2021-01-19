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
 * Copyright (c) 2021 Josh Bassett
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
 * Represents an unsigned 2D vector.
 *
 * @param xWidth The X data width.
 * @param yWidth The Y data width.
 */
class UVec2(xWidth: Int, yWidth: Int) extends Bundle {
  /** Horizontal position */
  val x = UInt(xWidth.W)
  /** Vertical position */
  val y = UInt(yWidth.W)

  def this(n: Int) = this(n, n)

  /** Addition operator. */
  def +(that: UVec2) = UVec2(this.x + that.x, this.y + that.y)

  /** Addition operator (expanding width). */
  def +&(that: UVec2) = UVec2(this.x +& that.x, this.y +& that.y)

  /** Subtraction operator. */
  def -(that: UVec2) = UVec2(this.x - that.x, this.y - that.y)

  /** Scalar multiplication operator. */
  def *(n: UInt) = UVec2(this.x * n, this.y * n)

  /** Left shift operator. */
  def <<(n: UInt) = UVec2((this.x << n).asUInt, (this.y << n).asUInt)

  override def cloneType: this.type = new UVec2(xWidth, yWidth).asInstanceOf[this.type]
}

object UVec2 {
  /**
   * Creates an unsigned vector from X and Y values.
   *
   * @param x The horizontal position.
   * @param y The vertical position.
   */
  def apply(x: UInt, y: UInt): UVec2 = {
    val pos = Wire(new UVec2(x.getWidth, y.getWidth))
    pos.x := x
    pos.y := y
    pos
  }

  /** Creates a zero vector. */
  def zero = UVec2(0.U, 0.U)
}
