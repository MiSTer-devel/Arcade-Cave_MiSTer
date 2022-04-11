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

import cave.Config
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
  /** EEPROM offset */
  val eepromOffset = UInt(32.W)
  /** Number of colors per palette */
  val numColors = UInt(9.W)
  /** Number of tilemap layers */
  val numLayers = UInt(2.W)
  /** Asserted when zoomed sprites are enabled */
  val spriteZoom = Bool()
  /** Sprite graphics format */
  val spriteFormat = UInt(Config.GFX_FORMAT_WIDTH.W)
  /** Layer 0 graphics format */
  val layer0Format = UInt(Config.GFX_FORMAT_WIDTH.W)
  /** Layer 1 graphics format */
  val layer1Format = UInt(Config.GFX_FORMAT_WIDTH.W)
  /** Layer 2 graphics format */
  val layer2Format = UInt(Config.GFX_FORMAT_WIDTH.W)
}

object GameConfig {
  /** Dangun Feveron */
  val DFEVERON = 0
  /** DoDonPachi */
  val DDONPACH = 1
  /** DonPachi */
  val DONPACHI = 2
  /** ESP Ra.De. */
  val ESPRADE = 3
  /** Puzzle Uo Poko */
  val UOPOKO = 4
  /** Guwange */
  val GUWANGE = 5

  def apply() = new GameConfig

  /**
   * Returns a game configuration for the given game index.
   *
   * @param index The game index.
   */
  def apply(index: UInt): GameConfig = {
    MuxLookup(index, ddonpach, Seq(
      DFEVERON.U -> dfeveron,
      ESPRADE.U -> esprade,
      GUWANGE.U -> guwange,
      UOPOKO.U -> uopoko
    ))
  }

  private def dfeveron = {
    val wire = Wire(new GameConfig)
    wire.index := DFEVERON.U
    wire.progRomOffset := 0x00000000.U
    wire.spriteRomOffset := 0x00100000.U
    wire.layer0RomOffset := 0x00900000.U
    wire.layer1RomOffset := 0x00b00000.U
    wire.layer2RomOffset := 0.U
    wire.soundRomOffset := 0x00d00000.U
    wire.eepromOffset := 0x01100000.U
    wire.numColors := 16.U
    wire.numLayers := 2.U
    wire.spriteZoom := true.B
    wire.spriteFormat := Config.GFX_FORMAT_4BPP.U
    wire.layer0Format := Config.GFX_FORMAT_4BPP.U
    wire.layer1Format := Config.GFX_FORMAT_4BPP.U
    wire.layer2Format := Config.GFX_FORMAT_UNKNOWN.U
    wire
  }

  private def ddonpach = {
    val wire = Wire(new GameConfig)
    wire.index := DDONPACH.U
    wire.progRomOffset := 0x00000000.U
    wire.spriteRomOffset := 0x00100000.U
    wire.layer0RomOffset := 0x00900000.U
    wire.layer1RomOffset := 0x00b00000.U
    wire.layer2RomOffset := 0x00d00000.U
    wire.soundRomOffset := 0x00f00000.U
    wire.eepromOffset := 0x01300000.U
    wire.numColors := 256.U
    wire.numLayers := 3.U
    wire.spriteZoom := false.B
    wire.spriteFormat := Config.GFX_FORMAT_4BPP_MSB.U
    wire.layer0Format := Config.GFX_FORMAT_4BPP.U
    wire.layer1Format := Config.GFX_FORMAT_4BPP.U
    wire.layer2Format := Config.GFX_FORMAT_8BPP.U
    wire
  }

  private def esprade = {
    val wire = Wire(new GameConfig)
    wire.index := ESPRADE.U
    wire.progRomOffset := 0x00000000.U
    wire.spriteRomOffset := 0x00100000.U
    wire.layer0RomOffset := 0x01100000.U
    wire.layer1RomOffset := 0x01900000.U
    wire.layer2RomOffset := 0x02100000.U
    wire.soundRomOffset := 0x02500000.U
    wire.eepromOffset := 0x02900000.U
    wire.numColors := 256.U
    wire.numLayers := 3.U
    wire.spriteZoom := true.B
    wire.spriteFormat := Config.GFX_FORMAT_8BPP.U
    wire.layer0Format := Config.GFX_FORMAT_8BPP.U
    wire.layer1Format := Config.GFX_FORMAT_8BPP.U
    wire.layer2Format := Config.GFX_FORMAT_8BPP.U
    wire
  }

  private def guwange = {
    val wire = Wire(new GameConfig)
    wire.index := GUWANGE.U
    wire.progRomOffset := 0x00000000.U
    wire.spriteRomOffset := 0x00100000.U
    wire.layer0RomOffset := 0x02100000.U
    wire.layer1RomOffset := 0x02900000.U
    wire.layer2RomOffset := 0x02d00000.U
    wire.soundRomOffset := 0x03100000.U
    wire.eepromOffset := 0x03500000.U
    wire.numColors := 256.U
    wire.numLayers := 3.U
    wire.spriteZoom := true.B
    wire.spriteFormat := Config.GFX_FORMAT_8BPP.U
    wire.layer0Format := Config.GFX_FORMAT_8BPP.U
    wire.layer1Format := Config.GFX_FORMAT_8BPP.U
    wire.layer2Format := Config.GFX_FORMAT_8BPP.U
    wire
  }

  private def uopoko = {
    val wire = Wire(new GameConfig)
    wire.index := UOPOKO.U
    wire.progRomOffset := 0x00000000.U
    wire.spriteRomOffset := 0x00100000.U
    wire.layer0RomOffset := 0x00500000.U
    wire.layer1RomOffset := 0x00500000.U
    wire.layer2RomOffset := 0x00500000.U
    wire.soundRomOffset := 0x00900000.U
    wire.eepromOffset := 0x00b00000.U
    wire.numColors := 256.U
    wire.numLayers := 1.U
    wire.spriteZoom := true.B
    wire.spriteFormat := Config.GFX_FORMAT_4BPP.U
    wire.layer0Format := Config.GFX_FORMAT_8BPP.U
    wire.layer1Format := Config.GFX_FORMAT_UNKNOWN.U
    wire.layer2Format := Config.GFX_FORMAT_UNKNOWN.U
    wire
  }
}
