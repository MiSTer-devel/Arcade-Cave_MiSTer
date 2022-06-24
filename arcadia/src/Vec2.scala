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

package arcadia

import chisel3._
import chisel3.internal.firrtl.Width

/** Abstract 2D vector. */
sealed abstract class Vec2[T <: Bits with Num[T]] extends Bundle {
  /** Horizontal position */
  val x: T
  /** Vertical position */
  val y: T

  /** Addition operator. */
  def +(that: Vec2[T]): Vec2[T]

  /** Subtraction operator. */
  def -(that: Vec2[T]): Vec2[T]

  /** Scalar multiplication operator. */
  def *(n: T): Vec2[T]

  /** Static left shift operator. */
  def <<(n: Int): Vec2[T]

  /** Static right shift operator. */
  def >>(n: Int): Vec2[T]
}

/**
 * Unsigned 2D vector.
 *
 * @param width The data width.
 */
class UVec2 private[arcadia](width: Width) extends Vec2[UInt] {
  /** Horizontal position */
  val x = UInt(width)
  /** Vertical position */
  val y = UInt(width)

  /** Addition operator. */
  def +(that: Vec2[UInt]) = UVec2(this.x + that.x, this.y + that.y)

  /** Subtraction operator. */
  def -(that: Vec2[UInt]) = UVec2(this.x - that.x, this.y - that.y)

  /** Scalar multiplication operator. */
  def *(n: UInt) = UVec2(this.x * n, this.y * n)

  /** Static left shift operator. */
  def <<(n: Int) = UVec2((this.x << n).asUInt, (this.y << n).asUInt)

  /** Static right shift operator. */
  def >>(n: Int) = UVec2((this.x >> n).asUInt, (this.y >> n).asUInt)
}

object UVec2 {
  /**
   * Creates an unsigned vector bundle.
   *
   * @param width The data width.
   * @return An unsigned vector bundle.
   */
  def apply(width: Width): UVec2 = new UVec2(width)

  /**
   * Creates an unsigned vector from X and Y values.
   *
   * @param x The horizontal position.
   * @param y The vertical position.
   * @return An unsigned vector.
   */
  def apply(x: Bits, y: Bits): UVec2 = {
    val width = Seq(x.getWidth, y.getWidth).max
    val vec = Wire(new UVec2(width.W))
    vec.x := x.asUInt
    vec.y := y.asUInt
    vec
  }
}

/**
 * Signed 2D vector.
 *
 * @param width The data width.
 */
class SVec2 private[arcadia](width: Width) extends Vec2[SInt] {
  /** Horizontal position */
  val x = SInt(width)
  /** Vertical position */
  val y = SInt(width)

  /** Addition operator. */
  def +(that: Vec2[SInt]) = SVec2(this.x + that.x, this.y + that.y)

  /** Subtraction operator. */
  def -(that: Vec2[SInt]) = SVec2(this.x - that.x, this.y - that.y)

  /** Scalar multiplication operator. */
  def *(n: SInt) = SVec2(this.x * n, this.y * n)

  /** Static left shift operator. */
  def <<(n: Int) = SVec2((this.x << n).asSInt, (this.y << n).asSInt)

  /** Static right shift operator. */
  def >>(n: Int) = SVec2((this.x >> n).asSInt, (this.y >> n).asSInt)
}

object SVec2 {
  /**
   * Creates a signed vector bundle.
   *
   * @param width The data width.
   * @return A signed vector bundle.
   */
  def apply(width: Width): SVec2 = new SVec2(width)

  /**
   * Creates a signed vector from X and Y values.
   *
   * @param x The horizontal position.
   * @param y The vertical position.
   * @return A signed vector.
   */
  def apply(x: Bits, y: Bits): SVec2 = {
    val width = Seq(x.getWidth, y.getWidth).max
    val vec = Wire(new SVec2(width.W))
    vec.x := x.asSInt
    vec.y := y.asSInt
    vec
  }
}
