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
import axon.types.UVec2
import cave.Config
import chisel3._

/** A bundle that represents the video registers. */
class VideoRegs extends Bundle {
  /** Layer offset */
  val layerOffset = UVec2(Config.LAYER_SCROLL_WIDTH.W)
  /** Sprite VRAM bank */
  val spriteBank = Bool()
}

object VideoRegs {
  /**
   * Decodes the video registers from the given data.
   *
   * {{{
   * word   bits                  description
   * -----+-fedc-ba98-7654-3210-+----------------
   *    0 | x--- ---- ---- ---- | flip x
   *      | -xxx xxxx xxxx xxxx | layer offset x
   *    1 | x--- ---- ---- ---- | flip y
   *      | -xxx xxxx xxxx xxxx | layer offset y
   *    4 | ---- ---- ---  --xx | sprite bank
   * }}}
   *
   * @param data The video registers data.
   */
  def decode(data: Bits): VideoRegs = {
    val words = Util.decode(data, 8, 16)
    val videoRegs = Wire(new VideoRegs)
    videoRegs.layerOffset := UVec2(words(0)(8, 0), words(1)(8, 0))
    videoRegs.spriteBank := words(4)(0)
    videoRegs
  }
}
