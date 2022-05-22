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

import axon.Util
import cave.Config
import chisel3._

/** A line effect contains the row-scroll/row-select values for a scanline. */
class LineEffect extends Bundle {
  /** Row select */
  val rowSelect = UInt(Config.LAYER_SCROLL_WIDTH.W)
  /** Row scroll */
  val rowScroll = UInt(Config.LAYER_SCROLL_WIDTH.W)
}

object LineEffect {
  def apply() = new LineEffect

  /**
   * Decodes a line effect from the given data.
   *
   * {{{
   * word   bits                  description
   * -----+-fedc-ba98-7654-3210-+----------------
   *    0 | xxxx xxxx xxxx xxxx | row scroll
   *    1 | xxxx xxxx xxxx xxxx | row select
   * }}}
   *
   * @param data The line effect data.
   */
  def decode(data: Bits): LineEffect = {
    val words = Util.decode(data, 2, 16)
    val lineEffect = Wire(new LineEffect)
    lineEffect.rowScroll := words(0)
    lineEffect.rowSelect := words(1)
    lineEffect
  }
}
