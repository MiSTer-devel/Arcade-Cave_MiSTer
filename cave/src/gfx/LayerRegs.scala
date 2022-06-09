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

import arcadia.UVec2
import cave.Config
import chisel3._

/** A bundle that contains the decoded layer registers. */
class LayerRegs extends Bundle {
  /** Priority */
  val priority = UInt(Config.PRIO_WIDTH.W)
  /** Tile size (8x8 or 16x16) */
  val tileSize = Bool()
  /** Enable flag */
  val enable = Bool()
  /** Horizontal flip */
  val flipX = Bool()
  /** Vertical flip */
  val flipY = Bool()
  /** Row scroll enable */
  val rowScrollEnable = Bool()
  /** Row select enable */
  val rowSelectEnable = Bool()
  /** Scroll position */
  val scroll = UVec2(Config.LAYER_SCROLL_WIDTH.W)
}

object LayerRegs {
  /**
   * Decodes the layer registers from the given data.
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
   *    2 | ---- ---- ---x ---- | enable
   *      | ---- ---- ---- --xx | priority
   * }}}
   *
   * @param data The layer registers data.
   */
  def decode(data: Vec[Bits]): LayerRegs = {
    val regs = Wire(new LayerRegs)
    regs.priority := data(2)(1, 0)
    regs.tileSize := data(1)(13)
    regs.enable := !data(2)(4)
    regs.flipX := !data(0)(15)
    regs.flipY := !data(1)(15)
    regs.rowScrollEnable := data(0)(14)
    regs.rowSelectEnable := data(1)(14)
    regs.scroll := UVec2(data(0)(8, 0), data(1)(8, 0))
    regs
  }
}
