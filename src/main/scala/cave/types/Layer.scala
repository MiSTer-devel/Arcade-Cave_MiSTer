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
import axon.types.UVec2
import cave.Config
import chisel3._
import chisel3.util._

/** Represents a layer descriptor. */
class Layer extends Bundle {
  /** Priority */
  val priority = UInt(Config.PRIO_WIDTH.W)
  /** Small tile flag */
  val smallTile = Bool()
  /** Disable flag */
  val disable = Bool()
  /** Horizontal flip */
  val flipX = Bool()
  /** Vertical flip */
  val flipY = Bool()
  /** Row scroll enable */
  val rowScrollEnable = Bool()
  /** Row select enable */
  val rowSelectEnable = Bool()
  /** Scroll position */
  val scroll = new UVec2(Config.LAYER_SCROLL_WIDTH)
}

object Layer {
  /**
   * Decodes a layer from the given data.
   *
   * {{{
   * word   bits                  description
   * -----+-fedc-ba98-7654-3210-+----------------
   *    0 | x--- ---- ---- ---- | flip x
   *      | -x-- ---- ---- ---- | row scroll enable
   *      | ---- ---x xxxx xxxx | scroll x
   *    1 | x--- ---- ---- ---- | flip y
   *      | -x-- ---- ---- ---- | row select enable
   *      | --x- ---- ---- ---- | tile size
   *      | ---- ---x xxxx xxxx | scroll y
   *    2 | ---- ---- ---x ---- | disable
   *      | ---- ---- ---- --xx | priority
   * }}}
   *
   * @param data The layer data.
   */
  def decode(data: Bits): Layer = {
    val words = Util.decode(data, 3, 16)
    val layer = Wire(new Layer)
    layer.priority := words(2)(1, 0)
    layer.smallTile := !words(1)(13)
    layer.disable := words(2)(4)
    layer.flipX := !words(0)(15)
    layer.flipY := !words(1)(15)
    layer.rowScrollEnable := words(0)(14)
    layer.rowSelectEnable := words(1)(14)
    layer.scroll := UVec2(words(0)(8, 0), words(1)(8, 0))
    layer
  }

  /**
   * Returns the magic offset value for the given layer index.
   *
   * The X offset in DDP is 0x195 for the first layer 0x195 = 405, 405 + 107 (0x6b) = 512.
   *
   * Due to pipeline pixel offsets, this must be incremented by 1 for each layer (and 8 once for
   * small tiles).
   *
   * The Y offset in DDP is 0x1EF = 495, 495 + 17 = 512.
   *
   * @param index The layer index.
   */
  def magicOffset(index: UInt): UVec2 = {
    val x = MuxLookup(index, 0.U, Seq(0.U -> 0x6b.U, 1.U -> 0x6c.U, 2.U -> 0x75.U))
    val y = 17.U
    UVec2(x, y)
  }
}
