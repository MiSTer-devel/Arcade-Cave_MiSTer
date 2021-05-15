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

/** Decodes 8x8 tiles from tile ROM data. */
class SmallTileDecoder extends Module {
  val io = IO(new Bundle {
    /** Graphics format port */
    val format = Input(UInt(3.W))
    /** Tile ROM data port */
    val rom = DeqIO(Bits(Config.TILE_ROM_DATA_WIDTH.W))
    /** Pixel data port */
    val pixelData = Flipped(DeqIO(Vec(Config.SMALL_TILE_SIZE, Bits(Config.TILE_MAX_BPP.W))))
  })

  // Set 8BPP flag
  val is8BPP = io.format === Config.GFX_FORMAT_8x8x8.U

  // Registers
  val pendingReg = RegInit(false.B)
  val toggleReg = RegInit(false.B)
  val validReg = RegInit(false.B)
  val dataReg = Reg(Bits(Config.TILE_ROM_DATA_WIDTH.W))
  val pixelDataReg = Reg(Vec(Config.SMALL_TILE_SIZE, Bits(Config.TILE_MAX_BPP.W)))

  // Extract the correct bits for a 4BPP tile
  val bits = Mux(toggleReg, dataReg, io.rom.bits.head(Config.TILE_ROM_DATA_WIDTH / 2))

  // The ready flag is asserted when a new request for pixel data, or there is no valid pixel data
  val ready = io.pixelData.ready || !validReg

  // The start flag is asserted when the decoder needs tile ROM data. For 8x8x4 tiles, the decoder
  // only needs to fetch tile ROM data for every other request.
  val start = ready && !pendingReg && (is8BPP || !toggleReg)

  // The done flag is asserted when the decoder has finished decoding a tile
  val done = pendingReg && toggleReg

  // Update the toggle register when a request is received
  when(io.pixelData.fire()) { toggleReg := !toggleReg }

  // Update the state when the decoder needs tile ROM data
  when(start) {
    pendingReg := true.B
    toggleReg := true.B
    validReg := false.B
  }

  // Update the state when there is valid tile ROM data
  when(io.rom.fire()) {
    pendingReg := false.B
    validReg := true.B
    dataReg := io.rom.bits
  }

  // Decode pixel data
  when(io.rom.fire() || (io.pixelData.ready && toggleReg)) {
    pixelDataReg := MuxLookup(io.format, VecInit(SmallTileDecoder.EMPTY), Seq(
      Config.GFX_FORMAT_8x8x4.U -> VecInit(SmallTileDecoder.decode_8x8x4(bits)),
      Config.GFX_FORMAT_8x8x8.U -> VecInit(SmallTileDecoder.decode_8x8x8(io.rom.bits))
    ))
  }

  // Outputs
  io.rom.ready := io.rom.valid && ready && (is8BPP || pendingReg || !toggleReg)
  io.pixelData.valid := validReg
  io.pixelData.bits := pixelDataReg

  printf(p"SmallTileDecoder(toggleReg: $toggleReg, pendingReg: $pendingReg, validReg: $validReg, start: $start, done: $done, romReady: ${ io.rom.ready }, romValid: ${ io.rom.valid }, pixReady: ${ io.pixelData.ready }, pixValid: ${ io.pixelData.valid })\n")
}

object SmallTileDecoder {
  /** Empty pixel data vector */
  val EMPTY = Seq.fill(8) { 0.U(8.W) }

  /** Decodes 8x8x4 tiles (i.e. 32 bits per row) */
  def decode_8x8x4(data: Bits): Seq[Bits] =
    Seq(0, 1, 2, 3, 4, 5, 6, 7)
      .reverse
      // Decode data into nibbles
      .map(Util.decode(data, 8, 4).apply)
      // Pad nibbles into 8-bit pixels
      .map(_.pad(8))

  /** Decodes 8x8x8 tiles (i.e. 64 bits per row) */
  def decode_8x8x8(data: Bits): Seq[Bits] =
    Seq(2, 0, 3, 1, 6, 4, 7, 5, 10, 8, 11, 9, 14, 12, 15, 13)
      .reverse
      // Decode data into nibbles
      .map(Util.decode(data, 16, 4).apply)
      // Join high/low nibbles into 8-bit pixels
      .grouped(2).map(Cat(_)).toSeq
}
