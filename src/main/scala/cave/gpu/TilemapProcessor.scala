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

package cave.gpu

import axon.Util
import axon.gfx.VideoIO
import axon.types._
import cave.Config
import cave.types._
import chisel3._
import chisel3.util._

/**
 * The tilemap processor renders tilemap layers.
 *
 * Tilemap layers are 512x512 pixels, and are composed of either 8x8 or 16x16 tiles with a color
 * depth of either 4 or 8 bits-per-pixel.
 */
class TilemapProcessor extends Module {
  val io = IO(new Bundle {
    /** Video port */
    val video = Input(new VideoIO)
    /** Layer port */
    val layer = LayerIO()
    /** Manual offset */
    val offset = Input(new UVec2(Config.LAYER_SCROLL_WIDTH))
    /** Palette entry output */
    val pen = Output(new PaletteEntry)
  })

  // Set enable flag
  val enable = io.layer.format =/= Config.GFX_FORMAT_UNKNOWN.U && io.layer.enable && !io.layer.regs.disable

  // Pixel position
  val pos = io.video.pos + io.layer.regs.scroll + io.offset

  // Pixel offset
  val offset = {
    val x = Mux(io.layer.regs.tileSize, pos.x(3, 0), pos.x(2, 0))
    val y = Mux(io.layer.regs.tileSize, pos.y(3, 0), pos.y(2, 0))
    UVec2(x, y)
  }

  // Layer RAM address
  val layerRamAddr = {
    val large = pos.y(8, 4) ## (pos.x(8, 4) + 1.U)
    val small = pos.y(8, 3) ## (pos.x(8, 3) + 1.U)
    Mux(io.layer.regs.tileSize, large, small)
  }

  // Latch signals
  val latchLayerRamAddr = io.video.pixelClockEnable && Mux(io.layer.regs.tileSize, offset.x === 8.U, offset.x === 0.U)
  val latchTile = io.video.pixelClockEnable && Mux(io.layer.regs.tileSize, offset.x === 10.U, offset.x === 2.U)
  val latchPix = io.video.pixelClockEnable && offset.x(2, 0) === 7.U
  val latchColor = io.video.pixelClockEnable && Mux(io.layer.regs.tileSize, offset.x === 15.U, offset.x === 7.U)

  // Registers
  val layerRamAddrReg = RegEnable(layerRamAddr, latchLayerRamAddr)
  val tileReg = RegEnable(Tile.decode(io.layer.ram.dout, io.layer.regs.tileSize), latchTile)
  val priorityReg = RegEnable(tileReg.priority, latchColor)
  val colorReg = RegEnable(tileReg.colorCode, latchColor)
  val pixReg = RegEnable(TilemapProcessor.decodePixels(io.layer.rom.dout, io.layer.format, offset), latchPix)

  // Palette entry
  val pen = PaletteEntry(priorityReg, colorReg, pixReg(offset.x(2, 0)))

  // Outputs
  io.layer.ram.rd := true.B // read-only
  io.layer.ram.addr := layerRamAddrReg
  io.layer.rom.rd := enable
  io.layer.rom.addr := TilemapProcessor.tileRomAddr(io.layer, tileReg.code, offset)
  io.pen := Mux(enable, pen, PaletteEntry.zero)
}

object TilemapProcessor {
  /**
   * Calculates the tile ROM byte address for the given tile code.
   *
   * @param code   The tile code.
   * @param offset The pixel offset.
   * @return A memory address.
   */
  private def tileRomAddr(layer: LayerIO, code: UInt, offset: UVec2): UInt = {
    val format_8x8x4 = !layer.regs.tileSize && layer.format === Config.GFX_FORMAT_4BPP.U
    val format_8x8x8 = !layer.regs.tileSize && layer.format === Config.GFX_FORMAT_8BPP.U
    val format_16x16x4 = layer.regs.tileSize && layer.format === Config.GFX_FORMAT_4BPP.U
    val format_16x16x8 = layer.regs.tileSize && layer.format === Config.GFX_FORMAT_8BPP.U

    MuxCase(0.U, Seq(
      format_8x8x4 -> code ## offset.y(2, 1) ## 0.U(3.W),
      format_8x8x8 -> code ## offset.y(2, 0) ## 0.U(3.W),
      format_16x16x4 -> code ## offset.y(3) ## ~offset.x(3) ## offset.y(2, 1) ## 0.U(3.W),
      format_16x16x8 -> code ## offset.y(3) ## ~offset.x(3) ## offset.y(2, 0) ## 0.U(3.W)
    ))
  }

  /**
   * Decodes a row of pixels from the given tile ROM data.
   *
   * @param data   The 64-bit tile ROM data.
   * @param format The graphics format.
   * @param offset The pixel offset.
   */
  private def decodePixels(data: Bits, format: UInt, offset: UVec2): Vec[Bits] = {
    val word = offset.y(0) // toggle the word bit on alternate scanlines
    val pixels_4BPP = VecInit(decode4BPP(data, word))
    val pixels_8BPP = VecInit(decode8BPP(data))
    Mux(format === Config.GFX_FORMAT_8BPP.U, pixels_8BPP, pixels_4BPP)
  }

  /**
   * Decodes a row of pixels for a 8x8x4BPP tile.
   *
   * A 64-bit word from the tile ROM contains two rows of pixels (i.e. 32 bits per row).
   *
   * @param data The 64-bit tile ROM data.
   * @param word A flag that indicates whether to decode the lower or upper 32-bits of the tile ROM
   *             data.
   */
  private def decode4BPP(data: Bits, word: Bool): Seq[Bits] = {
    val bits = Mux(word, data.tail(Config.TILE_ROM_DATA_WIDTH / 2), data.head(Config.TILE_ROM_DATA_WIDTH / 2))
    Seq(0, 1, 2, 3, 4, 5, 6, 7)
      // Decode data into nibbles
      .reverseIterator.map(Util.decode(bits, 8, 4).apply)
      // Pad nibbles into 8-bit pixels
      .map(_.pad(8)).toSeq
  }

  /**
   * Decodes a row of pixels for a 8x8x8BPP tile.
   *
   * A 64-bit word from the tile ROM contains one row of pixels (i.e. 64 bits per row).
   *
   * @param data The 64-bit tile ROM data.
   */
  private def decode8BPP(data: Bits): Seq[Bits] =
    Seq(2, 0, 3, 1, 6, 4, 7, 5, 10, 8, 11, 9, 14, 12, 15, 13)
      // Decode data into nibbles
      .reverseIterator.map(Util.decode(data, 16, 4).apply)
      // Join high/low nibbles into 8-bit pixels
      .grouped(2).map(Cat(_)).toSeq
}
