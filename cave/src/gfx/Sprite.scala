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

package cave.gfx

import arcadia._
import cave.Config
import chisel3._
import chisel3.util._

/** Represents a sprite descriptor. */
class Sprite extends Bundle {
  /** Priority */
  val priority = UInt(Config.PRIO_WIDTH.W)
  /** Color code */
  val colorCode = UInt(Config.PALETTE_WIDTH.W)
  /** Sprite code */
  val code = UInt(Sprite.CODE_WIDTH.W)
  /** Horizontal flip */
  val hFlip = Bool()
  /** Vertical flip */
  val vFlip = Bool()
  /** Position */
  val pos = SVec2(Sprite.POS_WIDTH.W)
  /** The number of sprite tile columns */
  val cols = UInt(Sprite.COLS_WIDTH.W)
  /** The number of sprite tile rows */
  val rows = UInt(Sprite.ROWS_WIDTH.W)
  /** Zoom */
  val zoom = UVec2(Sprite.ZOOM_WIDTH.W)

  /** Sprite size in pixels */
  def size: UVec2 = UVec2(cols, rows) << log2Ceil(Sprite.TILE_SIZE)

  /** Asserted when the sprite is enabled */
  def isEnabled: Bool = cols =/= 0.U && rows =/= 0.U
}

object Sprite {
  /** The size of a sprite tile in pixels */
  val TILE_SIZE = 16
  /** The width of the sprite code */
  val CODE_WIDTH = 18
  /** The width of the sprite position */
  val POS_WIDTH = 18
  /** The width of the sprite tile columns value */
  val COLS_WIDTH = 8
  /** The width of the sprite tile rows value */
  val ROWS_WIDTH = 8
  /** The width of the sprite zoom */
  val ZOOM_WIDTH = 16
  /** The number of fractional bits for sprite zoom values */
  val ZOOM_PRECISION = 8

  /**
   * Decodes a sprite from the given data.
   *
   * @param data   The sprite data.
   * @param zoomed A boolean value indicating whether the sprite is zoomed.
   * @param fixed  A boolean value indicating whether the sprite position is in fixed-point format.
   * @return The decoded sprite.
   */
  def decode(data: Bits, zoomed: Bool, fixed: Bool): Sprite = {
    val words = Util.decode(data, 8, 16)
    Mux(zoomed, decodeZoomedSprite(words, fixed), decodeSprite(words, fixed))
  }

  /**
   * Decodes a sprite.
   *
   * {{{
   * word   bits                  description
   * -----+-fedc-ba98-7654-3210-+----------------
   *    0 | --xx xxxx ---- ---- | color
   *      | ---- ---- --xx ---- | priority
   *      | ---- ---- ---- x--- | horizontal flip
   *      | ---- ---- ---- -x-- | vertical flip
   *      | ---- ---- ---- --xx | code hi
   *    1 | xxxx xxxx xxxx xxxx | code lo
   *    2 | ---- --xx xxxx xxxx | x position
   *    3 | ---- --xx xxxx xxxx | y position
   *    4 | xxxx xxxx ---- ---- | tile columns
   *      | ---- ---- xxxx xxxx | tile rows
   * }}}
   *
   * @param words The sprite data.
   * @param fixed A boolean value indicating whether the sprite position is in fixed-point format.
   */
  private def decodeSprite(words: Seq[Bits], fixed: Bool): Sprite = {
    val sprite = Wire(new Sprite)
    sprite.priority := words(0)(5, 4)
    sprite.colorCode := words(0)(13, 8)
    sprite.code := words(0)(1, 0) ## words(1)(15, 0)
    sprite.hFlip := words(0)(3)
    sprite.vFlip := words(0)(2)
    sprite.pos := decodePosition(words(2), words(3), fixed)
    sprite.cols := words(4)(15, 8)
    sprite.rows := words(4)(7, 0)
    sprite.zoom := UVec2(0x100.U, 0x100.U)
    sprite
  }

  /**
   * Decodes a zoomed sprite.
   *
   * {{{
   * word   bits                  description
   * -----+-fedc-ba98-7654-3210-+----------------
   *    0 | xxxx xxxx xxxx xxxx | x position
   *    1 | xxxx xxxx xxxx xxxx | y position
   *    2 | --xx xxxx ---- ---- | color
   *      | ---- ---- --xx ---- | priority
   *      | ---- ---- ---- x--- | flip x
   *      | ---- ---- ---- -x-- | flip y
   *      | ---- ---- ---- --xx | code hi
   *    3 | xxxx xxxx xxxx xxxx | code lo
   *    4 | xxxx xxxx xxxx xxxx | zoom x
   *    5 | xxxx xxxx xxxx xxxx | zoom y
   *    6 | xxxx xxxx ---- ---- | tile columns
   *      | ---- ---- xxxx xxxx | tile rows
   * }}}
   *
   * @param words The sprite data.
   * @param fixed A boolean value indicating whether the sprite position is in fixed-point format.
   */
  private def decodeZoomedSprite(words: Seq[Bits], fixed: Bool): Sprite = {
    val sprite = Wire(new Sprite)
    sprite.priority := words(2)(5, 4)
    sprite.colorCode := words(2)(13, 8)
    sprite.code := words(2)(1, 0) ## words(3)(15, 0)
    sprite.hFlip := words(2)(3)
    sprite.vFlip := words(2)(2)
    sprite.pos := decodePosition(words(0), words(1), fixed)
    sprite.cols := words(6)(15, 8)
    sprite.rows := words(6)(7, 0)
    sprite.zoom := UVec2(words(4), words(5))
    sprite
  }

  /**
   * Decodes a sprite position, formatted as a S9.6 fixed-point number or 10-bit signed integer,
   * into an 18-bit signed vector.
   *
   * @param x     The raw X value.
   * @param y     The raw Y value.
   * @param fixed A boolean value indicating whether the sprite position is in fixed-point format.
   * @return A signed vector.
   */
  private def decodePosition(x: Bits, y: Bits, fixed: Bool): SVec2 = Mux(
    fixed,
    SVec2(x ## 0.U(2.W), y ## 0.U(2.W)),
    SVec2(x(9, 0) ## 0.U(8.W), y(9, 0) ## 0.U(8.W))
  )
}
