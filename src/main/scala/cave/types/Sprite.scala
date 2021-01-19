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
  val tileSize = new UVec2(Config.SPRITE_TILE_SIZE_WIDTH)
  /** Zoom */
  val zoom = new UVec2(Config.SPRITE_ZOOM_WIDTH)

  /** Sprite size in pixels */
  def size: UVec2 = tileSize << log2Ceil(Config.LARGE_TILE_SIZE).U

  /** Asserted when the sprite is enabled */
  def isEnabled: Bool = tileSize.x =/= 0.U && tileSize.y =/= 0.U
}

object Sprite {
  /**
   * Decodes a sprite from the given data.
   *
   * @param data   The sprite data.
   * @param zoomed A boolean value indicating whether the sprite is zoomed.
   * @return The decoded sprite.
   */
  def decode(data: Bits, zoomed: Bool): Sprite = {
    val words = Util.decode(data, 8, 16)
    Mux(zoomed, decodeZoomedSprite(words), decodeSprite(words))
  }

  /**
   * Decodes a sprite.
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
   * }}}
   */
  private def decodeSprite(words: Seq[Bits]): Sprite = {
    val sprite = Wire(new Sprite)
    sprite.priority := words(0)(5, 4)
    sprite.colorCode := words(0)(13, 8)
    sprite.code := words(0)(1, 0) ## words(1)(15, 0)
    sprite.flipX := words(0)(3)
    sprite.flipY := words(0)(2)
    sprite.pos := SVec2(words(2)(9, 0).asSInt, words(3)(9, 0).asSInt)
    sprite.tileSize := UVec2(words(4)(15, 8), words(4)(7, 0))
    sprite.zoom := UVec2.zero
    sprite
  }

  /**
   * Decodes a zoomed sprite.
   *
   * {{{
   * word   bits                  description
   * -----+-fedc-ba98-7654-3210-+----------------
   *    0 | ---- --xx xxxx xxxx | x position
   *    1 | ---- --xx xxxx xxxx | y position
   *    2 | --xx xxxx ---- ---- | color
   *      | ---- ---- --xx ---- | priority
   *      | ---- ---- ---- x--- | flip x
   *      | ---- ---- ---- -x-- | flip y
   *      | ---- ---- ---- --xx | code hi
   *    3 | xxxx xxxx xxxx xxxx | code lo
   *    4 | xxxx xxxx xxxx xxxx | zoom x
   *    5 | xxxx xxxx xxxx xxxx | zoom y
   *    6 | xxxx xxxx ---- ---- | tile size x
   *      | ---- ---- xxxx xxxx | tile size y
   * }}}
   */
  private def decodeZoomedSprite(words: Seq[Bits]): Sprite = {
    val sprite = Wire(new Sprite)
    sprite.priority := words(2)(5, 4)
    sprite.colorCode := words(2)(13, 8)
    sprite.code := words(2)(1, 0) ## words(3)(15, 0)
    sprite.flipX := words(2)(3)
    sprite.flipY := words(2)(2)
    sprite.pos := SVec2(words(0)(9, 0).asSInt, words(1)(9, 0).asSInt)
    sprite.tileSize := UVec2(words(6)(15, 8), words(6)(7, 0))
    sprite.zoom := UVec2(words(4)(15, 0), words(5)(15, 0))
    sprite
  }
}
