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

  /** Left shift operator. */
  def <<(n: UInt) = SVec2((this.x << n).asSInt, (this.y << n).asSInt)

  override def cloneType: this.type = new SVec2(xWidth, yWidth).asInstanceOf[this.type]
}

object SVec2 {
  /**
   * Creates a signed vector from X and Y values.
   *
   * @param x The horizontal position.
   * @param y The vertical position.
   */
  def apply(x: SInt, y: SInt): SVec2 = {
    val pos = Wire(new SVec2(x.getWidth, y.getWidth))
    pos.x := x
    pos.y := y
    pos
  }

  /** Creates a zero vector. */
  def zero = SVec2(0.S, 0.S)
}
