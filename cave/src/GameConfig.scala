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

package cave

import arcadia.mister.OptionsIO
import chisel3._
import chisel3.util._

/** Represents a game configuration. */
class GameConfig extends Bundle {
  /** Game index */
  val index = UInt(OptionsIO.GAME_INDEX_WIDTH.W)
  /** The number of colors per palette */
  val granularity = UInt(9.W)
  /** Program ROM offset */
  val progRomOffset = UInt(32.W)
  /** EEPROM offset */
  val eepromOffset = UInt(32.W)
  /** Fill palette */
  val fillPalette = UInt(Config.PALETTE_WIDTH.W)
  /** Sound configuration */
  val sound = Vec(Config.SOUND_ROM_COUNT, new Bundle {
    /** Sound device */
    val device = UInt(2.W)
    /** Sound ROM offset */
    val romOffset = UInt(32.W)
  })
  /** Layer configuration */
  val layer = Vec(Config.LAYER_COUNT, new Bundle {
    /** Layer graphics format */
    val format = UInt(GameConfig.GFX_FORMAT_WIDTH.W)
    /** Layer ROM offset */
    val romOffset = UInt(32.W)
    /** Palette bank */
    val paletteBank = UInt(2.W)
  })
  /** Sprite configuration */
  val sprite = new Bundle {
    /** Sprite graphics format */
    val format = UInt(GameConfig.GFX_FORMAT_WIDTH.W)
    /** Sprite ROM offset */
    val romOffset = UInt(32.W)
    /** Asserted when sprite scaling is enabled */
    val zoom = Bool()
  }
}

object GameConfig {
  /** The width of the graphics format value */
  val GFX_FORMAT_WIDTH = 2

  def apply() = new GameConfig

  /**
   * Returns a game configuration for the given game index.
   *
   * @param index The game index.
   */
  def apply(index: UInt): GameConfig = {
    MuxLookup(index, dfeveron, Seq(
      Game.DDONPACH.U -> ddonpach,
      Game.DONPACHI.U -> donpachi,
      Game.ESPRADE.U -> esprade,
      Game.GAIA.U -> gaia,
      Game.GUWANGE.U -> guwange,
      Game.UOPOKO.U -> uopoko
    ))
  }

  private def dfeveron = {
    val wire = Wire(new GameConfig)
    wire.index := Game.DFEVERON.U
    wire.granularity := 16.U
    wire.progRomOffset := 0x00000000.U
    wire.eepromOffset := 0x00100000.U
    wire.fillPalette := 0x3f.U
    wire.sound(0).device := SoundDevice.YMZ280B.U
    wire.sound(1).device := SoundDevice.DISABLED.U
    wire.sound(0).romOffset := 0x00100080.U
    wire.sound(1).romOffset := 0.U
    wire.layer(0).format := GraphicsFormat.GFX_FORMAT_4BPP.U
    wire.layer(1).format := GraphicsFormat.GFX_FORMAT_4BPP.U
    wire.layer(2).format := GraphicsFormat.GFX_FORMAT_UNKNOWN.U
    wire.layer(0).paletteBank := 1.U
    wire.layer(1).paletteBank := 1.U
    wire.layer(2).paletteBank := 0.U
    wire.layer(0).romOffset := 0x00500080.U
    wire.layer(1).romOffset := 0x00700080.U
    wire.layer(2).romOffset := 0.U
    wire.sprite.format := GraphicsFormat.GFX_FORMAT_4BPP.U
    wire.sprite.romOffset := 0x00900080.U
    wire.sprite.zoom := true.B
    wire
  }

  private def ddonpach = {
    val wire = Wire(new GameConfig)
    wire.index := Game.DDONPACH.U
    wire.granularity := 256.U
    wire.progRomOffset := 0x00000000.U
    wire.eepromOffset := 0x00100000.U
    wire.fillPalette := 0x7f.U
    wire.sound(0).device := SoundDevice.YMZ280B.U
    wire.sound(1).device := SoundDevice.DISABLED.U
    wire.sound(0).romOffset := 0x00100080.U
    wire.sound(1).romOffset := 0.U
    wire.layer(0).format := GraphicsFormat.GFX_FORMAT_4BPP.U
    wire.layer(1).format := GraphicsFormat.GFX_FORMAT_4BPP.U
    wire.layer(2).format := GraphicsFormat.GFX_FORMAT_8BPP.U
    wire.layer(0).paletteBank := 1.U
    wire.layer(1).paletteBank := 1.U
    wire.layer(2).paletteBank := 1.U
    wire.layer(0).romOffset := 0x00500080.U
    wire.layer(1).romOffset := 0x00700080.U
    wire.layer(2).romOffset := 0x00900080.U
    wire.sprite.format := GraphicsFormat.GFX_FORMAT_4BPP_MSB.U
    wire.sprite.romOffset := 0x00b00080.U
    wire.sprite.zoom := false.B
    wire
  }

  private def donpachi = {
    val wire = Wire(new GameConfig)
    wire.index := Game.DONPACHI.U
    wire.granularity := 16.U
    wire.progRomOffset := 0x00000000.U
    wire.eepromOffset := 0x00080000.U
    wire.fillPalette := 0x7f.U
    wire.sound(0).device := SoundDevice.OKIM6259.U
    wire.sound(1).device := SoundDevice.OKIM6259.U
    wire.sound(0).romOffset := 0x00080080.U
    wire.sound(1).romOffset := 0x00280080.U
    wire.layer(0).format := GraphicsFormat.GFX_FORMAT_4BPP.U
    wire.layer(1).format := GraphicsFormat.GFX_FORMAT_4BPP.U
    wire.layer(2).format := GraphicsFormat.GFX_FORMAT_4BPP.U
    wire.layer(0).paletteBank := 1.U
    wire.layer(1).paletteBank := 1.U
    wire.layer(2).paletteBank := 1.U
    wire.layer(0).romOffset := 0x00380080.U
    wire.layer(1).romOffset := 0x00480080.U
    wire.layer(2).romOffset := 0x00580080.U
    wire.sprite.format := GraphicsFormat.GFX_FORMAT_4BPP_MSB.U
    wire.sprite.romOffset := 0x005c0080.U
    wire.sprite.zoom := false.B
    wire
  }

  private def esprade = {
    val wire = Wire(new GameConfig)
    wire.index := Game.ESPRADE.U
    wire.granularity := 256.U
    wire.progRomOffset := 0x00000000.U
    wire.eepromOffset := 0x00100000.U
    wire.fillPalette := 0x7f.U
    wire.sound(0).device := SoundDevice.YMZ280B.U
    wire.sound(1).device := SoundDevice.DISABLED.U
    wire.sound(0).romOffset := 0x00100080.U
    wire.sound(1).romOffset := 0.U
    wire.layer(0).format := GraphicsFormat.GFX_FORMAT_8BPP.U
    wire.layer(1).format := GraphicsFormat.GFX_FORMAT_8BPP.U
    wire.layer(2).format := GraphicsFormat.GFX_FORMAT_8BPP.U
    wire.layer(0).paletteBank := 1.U
    wire.layer(1).paletteBank := 1.U
    wire.layer(2).paletteBank := 1.U
    wire.layer(0).romOffset := 0x00500080.U
    wire.layer(1).romOffset := 0x00d00080.U
    wire.layer(2).romOffset := 0x01500080.U
    wire.sprite.format := GraphicsFormat.GFX_FORMAT_8BPP.U
    wire.sprite.romOffset := 0x01900080.U
    wire.sprite.zoom := true.B
    wire
  }

  private def gaia = {
    val wire = Wire(new GameConfig)
    wire.index := Game.GAIA.U
    wire.granularity := 256.U
    wire.progRomOffset := 0x00000000.U
    wire.eepromOffset := 0.U // disabled
    wire.fillPalette := 0x7f.U
    wire.sound(0).device := SoundDevice.YMZ280B.U
    wire.sound(1).device := SoundDevice.DISABLED.U
    wire.sound(0).romOffset := 0x00100000.U
    wire.sound(1).romOffset := 0.U
    wire.layer(0).format := GraphicsFormat.GFX_FORMAT_8BPP.U
    wire.layer(1).format := GraphicsFormat.GFX_FORMAT_8BPP.U
    wire.layer(2).format := GraphicsFormat.GFX_FORMAT_8BPP.U
    wire.layer(0).paletteBank := 1.U
    wire.layer(1).paletteBank := 1.U
    wire.layer(2).paletteBank := 1.U
    wire.layer(0).romOffset := 0x00d00000.U
    wire.layer(1).romOffset := 0x01100000.U
    wire.layer(2).romOffset := 0x01500000.U
    wire.sprite.format := GraphicsFormat.GFX_FORMAT_4BPP.U
    wire.sprite.romOffset := 0x01900000.U
    wire.sprite.zoom := true.B
    wire
  }

  private def guwange = {
    val wire = Wire(new GameConfig)
    wire.index := Game.GUWANGE.U
    wire.granularity := 256.U
    wire.progRomOffset := 0x00000000.U
    wire.eepromOffset := 0x00100000.U
    wire.fillPalette := 0x7f.U
    wire.sound(0).device := SoundDevice.YMZ280B.U
    wire.sound(1).device := SoundDevice.DISABLED.U
    wire.sound(0).romOffset := 0x00100080.U
    wire.sound(1).romOffset := 0.U
    wire.layer(0).format := GraphicsFormat.GFX_FORMAT_8BPP.U
    wire.layer(1).format := GraphicsFormat.GFX_FORMAT_8BPP.U
    wire.layer(2).format := GraphicsFormat.GFX_FORMAT_8BPP.U
    wire.layer(0).paletteBank := 1.U
    wire.layer(1).paletteBank := 1.U
    wire.layer(2).paletteBank := 1.U
    wire.layer(0).romOffset := 0x00500080.U
    wire.layer(1).romOffset := 0x00d00080.U
    wire.layer(2).romOffset := 0x01100080.U
    wire.sprite.format := GraphicsFormat.GFX_FORMAT_8BPP.U
    wire.sprite.romOffset := 0x01500080.U
    wire.sprite.zoom := true.B
    wire
  }

  private def uopoko = {
    val wire = Wire(new GameConfig)
    wire.index := Game.UOPOKO.U
    wire.granularity := 256.U
    wire.progRomOffset := 0x00000000.U
    wire.eepromOffset := 0x00100000.U
    wire.fillPalette := 0x7f.U
    wire.sound(0).device := SoundDevice.YMZ280B.U
    wire.sound(1).device := SoundDevice.DISABLED.U
    wire.sound(0).romOffset := 0x00100080.U
    wire.sound(1).romOffset := 0.U
    wire.layer(0).format := GraphicsFormat.GFX_FORMAT_8BPP.U
    wire.layer(1).format := GraphicsFormat.GFX_FORMAT_UNKNOWN.U
    wire.layer(2).format := GraphicsFormat.GFX_FORMAT_UNKNOWN.U
    wire.layer(0).paletteBank := 1.U
    wire.layer(1).paletteBank := 0.U
    wire.layer(2).paletteBank := 0.U
    wire.layer(0).romOffset := 0x00300080.U
    wire.layer(1).romOffset := 0.U
    wire.layer(2).romOffset := 0.U
    wire.sprite.format := GraphicsFormat.GFX_FORMAT_4BPP.U
    wire.sprite.romOffset := 0x00700080.U
    wire.sprite.zoom := true.B
    wire
  }
}
