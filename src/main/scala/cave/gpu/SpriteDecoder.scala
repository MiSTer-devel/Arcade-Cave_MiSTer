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
import chisel3._
import chisel3.util._

/**
 * Decodes sprites tiles from tile ROM data.
 *
 * Tile data is accessed using the 64-bit `rom` port. Some tiles require more than one 64-bit word
 * to be decoded:
 *
 *   - 16x16x4: 64-bits (1 word)
 *   - 16x16x8: 128-bits (2 words)
 */
class SpriteDecoder extends Module {
  val io = IO(new Bundle {
    /** Graphics format port */
    val format = Input(UInt(Config.GFX_FORMAT_WIDTH.W))
    /** Tile ROM data port */
    val rom = DeqIO(Bits(Config.TILE_ROM_DATA_WIDTH.W))
    /** Pixel data port */
    val pixelData = Flipped(DeqIO(Vec(Config.LARGE_TILE_SIZE, Bits(Config.TILE_MAX_BPP.W))))
  })

  // Set 8BPP flag
  val is8BPP = io.format === Config.GFX_FORMAT_SPRITE_8BPP.U

  // Registers
  val pendingReg = RegInit(false.B)
  val toggleReg = RegInit(false.B)
  val validReg = RegInit(false.B)
  val pixelDataReg = Reg(Vec(Config.LARGE_TILE_SIZE, Bits(Config.TILE_MAX_BPP.W)))

  // The ready flag is asserted when a new request for pixel data, or there is no valid pixel data
  val ready = io.pixelData.ready || !validReg

  // The start flag is asserted when the decoder wants to decode a tile
  val start = ready && !pendingReg

  // The done flag is asserted when the decoder has finished decoding a tile
  val done = !is8BPP || (pendingReg && toggleReg)

  // Clear the pending/toggle registers
  when(start) {
    pendingReg := true.B
    toggleReg := false.B
    validReg := false.B
  }

  // Update the state when there is valid tile ROM data
  when(io.rom.fire() && !done) {
    toggleReg := true.B
  }.elsewhen(io.rom.fire() && done) {
    pendingReg := false.B
    toggleReg := false.B
    validReg := true.B
  }

  // Set the data register
  when(io.rom.fire()) {
    pixelDataReg := MuxLookup(io.format, VecInit(SpriteDecoder.decode4BPP(io.rom.bits)), Seq(
      Config.GFX_FORMAT_SPRITE_MSB.U -> VecInit(SpriteDecoder.decode4BPPMSB(io.rom.bits)),
      Config.GFX_FORMAT_SPRITE_8BPP.U -> VecInit(SpriteDecoder.decode8BPP(RegNext(io.rom.bits) ## io.rom.bits))
    ))
  }

  // Outputs
  io.rom.ready := io.rom.valid && ready
  io.pixelData.valid := validReg
  io.pixelData.bits := pixelDataReg

  printf(p"SpriteDecoder(toggleReg: $toggleReg, pendingReg: $pendingReg, validReg: $validReg, start: $start, done: $done, romReady: ${ io.rom.ready }, romValid: ${ io.rom.valid }, pixReady: ${ io.pixelData.ready }, pixValid: ${ io.pixelData.valid })\n")
}

object SpriteDecoder {
  /** Decode 16x16x4 tiles (i.e. 64 bits per row) */
  private def decode4BPP(data: Bits): Seq[Bits] =
    Seq(1, 0, 3, 2, 5, 4, 7, 6, 9, 8, 11, 10, 13, 12, 15, 14)
      .reverse
      // Decode data into nibbles
      .map(Util.decode(data, 16, 4).apply)
      // Pad nibbles into 8-bit pixels
      .map(_.pad(8))

  /** Decode 16x16x4 MSB tile (i.e. 64 bits per row) */
  private def decode4BPPMSB(data: Bits): Seq[Bits] =
    Seq(2, 3, 0, 1, 6, 7, 4, 5, 10, 11, 8, 9, 14, 15, 12, 13)
      .reverse
      // Decode data into nibbles
      .map(Util.decode(data, 16, 4).apply)
      // Pad nibbles into 8-bit pixels
      .map(_.pad(8))

  /** Decode 16x16x8 MSB sprite (i.e. 128 bits per row) */
  private def decode8BPP(data: Bits): Seq[Bits] =
    Seq(1, 3, 0, 2, 5, 7, 4, 6, 9, 11, 8, 10, 13, 15, 12, 14, 17, 19, 16, 18, 21, 23, 20, 22, 25, 27, 24, 26, 29, 31, 28, 30)
      .reverse
      // Decode data into nibbles
      .map(Util.decode(data, 32, 4).apply)
      // Join high/low nibbles into 8-bit pixels
      .grouped(2).map(Cat(_)).toSeq
}
