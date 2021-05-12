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

package cave.gpu

import axon.Util
import cave.Config
import cave.types._
import chisel3._
import chisel3.util._

/** The tile decoder decodes pixels from the tile ROM. */
class TileDecoder extends Module {
  val io = IO(new Bundle {
    /** Game config port */
    val gameConfig = Input(GameConfig())
    /** Pixel data port */
    val pixelData = Flipped(DeqIO(Vec(Config.SPRITE_TILE_SIZE, Bits(Config.SPRITE_MAX_BPP.W))))
    /** Tile ROM data port */
    val rom = DeqIO(Bits(Config.TILE_ROM_DATA_WIDTH.W))
  })

  // Set 8BPP tile format flag
  val tileFormat8BPP = io.gameConfig.spriteFormat === GameConfig.GFX_FORMAT_SPRITE_8BPP.U

  // Registers
  val validReg = RegInit(false.B)
  val dataReg = Reg(Vec(Config.SPRITE_TILE_SIZE, Bits(Config.SPRITE_MAX_BPP.W)))

  // The pending flag is asserted when the decoder is waiting for tile ROM data
  val pending = io.pixelData.ready && (!io.rom.valid || tileFormat8BPP)

  // The done flag is asserted when the decoder has finished loading tile ROM data (one word for a
  // 4BPP tile, two words for a 8BPP tile)
  val done = Mux(tileFormat8BPP, Util.toggle(io.rom.fire()), io.rom.fire())

  // Toggle the valid register
  when(!validReg && done) {
    validReg := true.B
  }.elsewhen(validReg && pending) {
    validReg := false.B
  }

  // Set the data register
  when(io.rom.fire()) {
    dataReg := MuxLookup(io.gameConfig.spriteFormat, VecInit(TileDecoder.decodeSpriteTile(io.rom.bits)), Seq(
      GameConfig.GFX_FORMAT_SPRITE_MSB.U -> VecInit(TileDecoder.decodeSpriteMSBTile(io.rom.bits)),
      GameConfig.GFX_FORMAT_SPRITE_8BPP.U -> VecInit(TileDecoder.decodeSprite8BPPTile(RegNext(io.rom.bits) ## io.rom.bits))
    ))
  }

  // Outputs
  io.pixelData.valid := validReg
  io.pixelData.bits := dataReg
  io.rom.ready := !validReg || io.pixelData.ready

  printf(p"TileDecoder(pixReady: ${ io.pixelData.ready }, pixValid: ${ io.pixelData.valid }, romReady: ${ io.rom.ready }, romValid: ${ io.rom.valid })\n")
}

object TileDecoder {
  private def decodeSpriteTile(data: Bits): Seq[Bits] =
    Seq(1, 0, 3, 2, 5, 4, 7, 6, 9, 8, 11, 10, 13, 12, 15, 14)
      .reverse
      // Decode data into nibbles
      .map(Util.decode(data, 16, 4).apply)
      // Pad nibbles into 8-bit pixels
      .map(_.pad(8))

  private def decodeSpriteMSBTile(data: Bits): Seq[Bits] =
    Seq(2, 3, 0, 1, 6, 7, 4, 5, 10, 11, 8, 9, 14, 15, 12, 13)
      .reverse
      // Decode data into nibbles
      .map(Util.decode(data, 16, 4).apply)
      // Pad nibbles into 8-bit pixels
      .map(_.pad(8))

  private def decodeSprite8BPPTile(data: Bits): Seq[Bits] =
    Seq(1, 3, 0, 2, 5, 7, 4, 6, 9, 11, 8, 10, 13, 15, 12, 14, 17, 19, 16, 18, 21, 23, 20, 22, 25, 27, 24, 26, 29, 31, 28, 30)
      .reverse
      // Decode data into nibbles
      .map(Util.decode(data, 32, 4).apply)
      // Join high/low nibbles into 8-bit pixels
      .grouped(2).map(Cat(_)).toSeq
}
