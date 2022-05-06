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

import axon.mem.ReadMemIO
import cave.Config
import chisel3._

/** A bundle that contains control signals for the sprite processor. */
class SpriteCtrlIO extends Bundle {
  /** Graphics format */
  val format = Input(UInt(Config.GFX_FORMAT_WIDTH.W))
  /** Enable the layer output */
  val enable = Input(Bool())
  /** Start a new frame */
  val start = Input(Bool())
  /** Asserted when the sprite processor is busy */
  val busy = Output(Bool())
  /** Flip the layer */
  val flip = Input(Bool())
  /** Rotate the layer 90 degrees */
  val rotate = Input(Bool())
  /** Enable sprite scaling */
  val zoom = Input(Bool())
  /** VRAM port */
  val vram = ReadMemIO(Config.SPRITE_RAM_GPU_ADDR_WIDTH, Config.SPRITE_RAM_GPU_DATA_WIDTH)
  /** Tile ROM port */
  val tileRom = new SpriteRomIO
}

object SpriteCtrlIO {
  def apply() = new SpriteCtrlIO
}