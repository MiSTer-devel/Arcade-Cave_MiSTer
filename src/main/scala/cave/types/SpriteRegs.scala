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

package cave.types

import axon.types.UVec2
import cave.Config
import chisel3._

/** A bundle that contains the decoded sprite registers. */
class SpriteRegs extends Bundle {
  /** Layer offset */
  val layerOffset = UVec2(Config.LAYER_SCROLL_WIDTH.W)
  /** Sprite bank */
  val bank = UInt(SpriteRegs.SPRITE_BANK_WIDTH.W)
  /** Sprite fixed-point position format  */
  val fixed = Bool()
}

object SpriteRegs {
  /** The width of the sprite bank */
  val SPRITE_BANK_WIDTH = 2

  /**
   * Decodes the sprite registers from the given data.
   *
   * {{{
   * word   bits                  description
   * -----+-fedc-ba98-7654-3210-+----------------
   *    0 | x--- ---- ---- ---- | flip x
   *      | -xxx xxxx xxxx xxxx | layer offset x
   *    1 | x--- ---- ---- ---- | flip y
   *      | -xxx xxxx xxxx xxxx | layer offset y
   *    4 | ---- ---- ---- --xx | sprite bank
   *    5 | --xx ---- ---- ---- | sprite position format
   * }}}
   *
   * @param regs The sprite registers data.
   */
  def decode(regs: Vec[Bits]): SpriteRegs = {
    val videoRegs = Wire(new SpriteRegs)
    videoRegs.layerOffset := UVec2(regs(0)(8, 0), regs(1)(8, 0))
    videoRegs.bank := regs(4)(1, 0)
    videoRegs.fixed := regs(5)(13, 12).orR
    videoRegs
  }
}
