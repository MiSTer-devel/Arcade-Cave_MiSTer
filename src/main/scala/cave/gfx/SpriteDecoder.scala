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
import chisel3.util._

/**
 * Decodes sprite pixel data from the tile ROM.
 *
 * Tile ROM data is read from a 64-bit FIFO, decoded, and written to another FIFO as 16x8BPP strides
 * of pixel data, to be processed by the sprite blitter.
 */
class SpriteDecoder extends Module {
  val io = IO(new Bundle {
    /** Graphics format port */
    val format = Input(UInt(Config.GFX_FORMAT_WIDTH.W))
    /** Tile ROM data port */
    val tileRom = DeqIO(Bits(Config.TILE_ROM_DATA_WIDTH.W))
    /** Pixel data port */
    val pixelData = Flipped(DeqIO(Vec(Config.LARGE_TILE_SIZE, Bits(Config.TILE_MAX_BPP.W))))
  })

  // Set 8BPP flag
  val is8BPP = io.format === Config.GFX_FORMAT_8BPP.U

  // Registers
  val pendingReg = RegInit(false.B)
  val validReg = RegInit(false.B)
  val toggleReg = RegInit(false.B)
  val dataReg = Reg(Bits((Config.TILE_ROM_DATA_WIDTH * 2).W))

  // The start flag is asserted when there is no valid pixel data
  val start = !validReg && !pendingReg

  // The ready flag is asserted when the decoder needs to fetch tile ROM data.
  //
  // For 4BPP sprites we need to fetch one 64-bit word for every row, and for 8BPP we need to fetch
  // two 64-bit words to decode a row.
  val ready = io.pixelData.ready

  // The done flag is asserted when the decoder has finished decoding a tile
  val done = !is8BPP || toggleReg

  // Decode the tile ROM data
  val bits = MuxLookup(io.format, VecInit(SpriteDecoder.decode4BPP(dataReg.tail(Config.TILE_ROM_DATA_WIDTH))), Seq(
    Config.GFX_FORMAT_4BPP_MSB.U -> VecInit(SpriteDecoder.decode4BPPMSB(dataReg.tail(Config.TILE_ROM_DATA_WIDTH))),
    Config.GFX_FORMAT_8BPP.U -> VecInit(SpriteDecoder.decode8BPP(dataReg))
  ))

  // Clear registers when starting a new request
  when(start || ready) {
    pendingReg := true.B
    validReg := false.B
    toggleReg := false.B
  }

  // Update the state when there is valid tile ROM data
  when(io.tileRom.fire) {
    when(done) {
      pendingReg := false.B
      validReg := true.B
    }
    dataReg := dataReg.tail(Config.TILE_ROM_DATA_WIDTH) ## io.tileRom.bits
    toggleReg := !toggleReg
  }

  // Outputs
  io.tileRom.ready := pendingReg
  io.pixelData.valid := validReg
  io.pixelData.bits := bits

  printf(p"SpriteDecoder(start: $start, ready: $ready, done: $done, pending: $pendingReg, valid: $validReg, toggle: $toggleReg, romReady: ${ io.tileRom.ready }, romValid: ${ io.tileRom.valid }, pixReady: ${ io.pixelData.ready }, pixValid: ${ io.pixelData.valid }), data: 0x${ Hexadecimal(dataReg) })\n")
}

object SpriteDecoder {
  /** Decode 16x16x4 tiles (i.e. 64 bits per row) */
  private def decode4BPP(data: Bits): Seq[Bits] =
    Seq(15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1, 0)
      // Decode data into nibbles
      .reverseIterator.map(Util.decode(data, 16, 4).apply)
      // Pad nibbles into 8-bit pixels
      .map(_.pad(8)).toSeq

  /** Decode 16x16x4 MSB tile (i.e. 64 bits per row) */
  private def decode4BPPMSB(data: Bits): Seq[Bits] =
    Seq(12, 13, 14, 15, 8, 9, 10, 11, 4, 5, 6, 7, 0, 1, 2, 3)
      // Decode data into nibbles
      .reverseIterator.map(Util.decode(data, 16, 4).apply)
      // Pad nibbles into 8-bit pixels
      .map(_.pad(8)).toSeq

  /** Decode 16x16x8 MSB sprite (i.e. 128 bits per row) */
  private def decode8BPP(data: Bits): Seq[Bits] =
    Seq(15, 13, 14, 12, 11, 9, 10, 8, 7, 5, 6, 4, 3, 1, 2, 0, 31, 29, 30, 28, 27, 25, 26, 24, 23, 21, 22, 20, 19, 17, 18, 16)
      // Decode data into nibbles
      .reverseIterator.map(Util.decode(data, 32, 4).apply)
      // Join high/low nibbles into 8-bit pixels
      .grouped(2).map(Cat(_)).toSeq
}
