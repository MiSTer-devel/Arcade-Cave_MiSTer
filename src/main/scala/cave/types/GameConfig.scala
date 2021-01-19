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

import chisel3._
import chisel3.util._

/** Represents a game configuration. */
class GameConfig extends Bundle {
  /** Game index */
  val index = UInt(4.W)
  /** Program ROM offset */
  val progRomOffset = UInt(32.W)
  /** Sprite ROM offset */
  val spriteRomOffset = UInt(32.W)
  /** Layer 0 ROM offset */
  val layer0RomOffset = UInt(32.W)
  /** Layer 1 ROM offset */
  val layer1RomOffset = UInt(32.W)
  /** Layer 2 ROM offset */
  val layer2RomOffset = UInt(32.W)
  /** Sound ROM offset */
  val soundRomOffset = UInt(32.W)
  /** Number of colors per palette */
  val numColors = UInt(9.W)
  /** Number of tilemap layers */
  val numLayers = UInt(2.W)
  /** Asserted when zoomed sprites are enabled */
  val spriteZoom = Bool()
}

object GameConfig {
  /** DoDonPachi */
  val DODONPACHI = 0
  /** Dangun Feveron */
  val DANGUN_FEVERON = 1
  /** ESP Ra.De. */
  val ESPRADE = 2

  def apply() = new GameConfig

  /**
   * Returns a game configuration for the given game index.
   *
   * @param index The game index.
   */
  def apply(index: UInt): GameConfig = {
    MuxLookup(index, dodonpachiConfig, Seq(
      DANGUN_FEVERON.U -> dangunFeveronConfig,
      ESPRADE.U -> espradeConfig
    ))
  }

  private def dodonpachiConfig = {
    val wire = Wire(new GameConfig)
    wire.index := DODONPACHI.U
    wire.progRomOffset := 0x00000000.U
    wire.spriteRomOffset := 0x00100000.U
    wire.layer0RomOffset := 0x00900000.U
    wire.layer1RomOffset := 0x00b00000.U
    wire.layer2RomOffset := 0x00d00000.U
    wire.soundRomOffset := 0x00f00000.U
    wire.numColors := 256.U
    wire.numLayers := 3.U
    wire.spriteZoom := false.B
    wire
  }

  private def dangunFeveronConfig = {
    val wire = Wire(new GameConfig)
    wire.index := DANGUN_FEVERON.U
    wire.progRomOffset := 0x00000000.U
    wire.spriteRomOffset := 0x00100000.U
    wire.layer0RomOffset := 0x00900000.U
    wire.layer1RomOffset := 0x00b00000.U
    wire.layer2RomOffset := 0.U
    wire.soundRomOffset := 0x00d00000.U
    wire.numColors := 16.U
    wire.numLayers := 2.U
    wire.spriteZoom := true.B
    wire
  }

  private def espradeConfig = {
    val wire = Wire(new GameConfig)
    wire.index := ESPRADE.U
    wire.progRomOffset := 0x00000000.U
    wire.spriteRomOffset := 0x00100000.U
    wire.layer0RomOffset := 0x01100000.U
    wire.layer1RomOffset := 0x01900000.U
    wire.layer2RomOffset := 0x02100000.U
    wire.soundRomOffset := 0x02500000.U
    wire.numColors := 256.U
    wire.numLayers := 3.U
    wire.spriteZoom := true.B
    wire
  }
}
