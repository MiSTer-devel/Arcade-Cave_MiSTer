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

import arcadia.mem.ReadMemIO
import cave._
import chisel3._

/** A bundle that contains control signals for the layer processor. */
class LayerCtrlIO extends Bundle {
  /** Enable the layer output */
  val enable = Input(Bool())
  /** Graphics format */
  val format = Input(UInt(GameConfig.GFX_FORMAT_WIDTH.W))
  /** Layer registers port */
  val regs = Input(new LayerRegs)
  /** 8x8 VRAM port */
  val vram8x8 = ReadMemIO(Config.LAYER_8x8_RAM_GPU_ADDR_WIDTH, Config.LAYER_RAM_GPU_DATA_WIDTH)
  /** 16x16 VRAM port */
  val vram16x16 = ReadMemIO(Config.LAYER_16x16_RAM_GPU_ADDR_WIDTH, Config.LAYER_RAM_GPU_DATA_WIDTH)
  /** Line RAM port */
  val lineRam = ReadMemIO(Config.LINE_RAM_GPU_ADDR_WIDTH, Config.LINE_RAM_GPU_DATA_WIDTH)
  /** Tile ROM port */
  val tileRom = new TileRomIO
}

object LayerCtrlIO {
  def apply() = new LayerCtrlIO
}
