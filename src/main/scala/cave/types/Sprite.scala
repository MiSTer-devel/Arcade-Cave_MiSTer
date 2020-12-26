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

package cave.types

import axon.Util
import axon.types._
import cave.Config
import chisel3._
import chisel3.util._

/** Represents a sprite descriptor. */
class Sprite extends Bundle {
  /** Priority */
  val priority = UInt(Config.PRIO_WIDTH.W)
  /** Color code */
  val colorCode = UInt(Config.COLOR_CODE_WIDTH.W)
  /** Tile code */
  val code = UInt(Config.SPRITE_CODE_WIDTH.W)
  /** Horizontal flip */
  val flipX = Bool()
  /** Vertical flip */
  val flipY = Bool()
  /** Position */
  val pos = new SVec2(Config.SPRITE_POS_WIDTH)
  /** Tile size */
  val tileSize = new Vec2(Config.SPRITE_TILE_SIZE_WIDTH)
  /** Zoom */
  val zoom = new Vec2(Config.SPRITE_ZOOM_WIDTH)

  /** Sprite size in pixels */
  def size: Vec2 = tileSize << log2Ceil(Config.LARGE_TILE_SIZE).U

  /** Asserted when the sprite is enabled */
  def isEnabled: Bool = tileSize.x =/= 0.U && tileSize.y =/= 0.U
}

object Sprite {
  /**
   * Decodes a sprite from the given data.
   *
   * {{{
   * word   bits                  description
   * -----+-fedc-ba98-7654-3210-+----------------
   *    0 | --xx xxxx ---- ---- | color
   *      | ---- ---- --xx ---- | priority
   *      | ---- ---- ---- x--- | flip x
   *      | ---- ---- ---- -x-- | flip y
   *      | ---- ---- ---- --xx | code hi
   *    1 | xxxx xxxx xxxx xxxx | code lo
   *    2 | ---- --xx xxxx xxxx | x position
   *    3 | ---- --xx xxxx xxxx | y position
   *    4 | xxxx xxxx ---- ---- | tile size x
   *      | ---- ---- xxxx xxxx | tile size y
   *    5 | ---- ---- ---- - -- |
   *    6 | xxxx xxxx xxxx xxxx | zoom x
   *    7 | xxxx xxxx xxxx xxxx | zoom y
   * }}}
   *
   * @param data The sprite data.
   */
  def decode(data: Bits): Sprite = {
    val words = Util.decode(data, 8, 16)
    val sprite = Wire(new Sprite)
    sprite.priority := words(0)(5, 4)
    sprite.colorCode := words(0)(13, 8)
    sprite.code := words(0)(1, 0) ## words(1)(15, 0)
    sprite.flipX := words(0)(3)
    sprite.flipY := words(0)(2)
    sprite.pos := SVec2(words(2)(9, 0).asSInt, words(3)(9, 0).asSInt)
    sprite.tileSize := Vec2(words(4)(15, 8), words(4)(7, 0))
    sprite.zoom := Vec2(words(6)(15, 0), words(7)(15, 0))
    sprite
  }
}
