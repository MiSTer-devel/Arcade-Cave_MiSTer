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
 * Decodes tilemap tiles from tile ROM data.
 *
 * Tile ROM data is accessed using the 64-bit `rom` port and decoded:
 *
 *   - 8x8x4: 32-bits (0.5 words)
 *   - 8x8x8: 64-bits (1 words)
 */
class TilemapDecoder extends Module {
  val io = IO(new Bundle {
    /** Graphics format port */
    val format = Input(UInt(Config.GFX_FORMAT_WIDTH.W))
    /** Tile ROM data port */
    val rom = DeqIO(Bits(Config.TILE_ROM_DATA_WIDTH.W))
    /** Pixel data port */
    val pixelData = Flipped(DeqIO(Vec(Config.SMALL_TILE_SIZE, Bits(Config.TILE_MAX_BPP.W))))
  })

  // Set 8BPP flag
  val is8BPP = io.format === Config.GFX_FORMAT_8BPP.U

  // Registers
  val pendingReg = RegInit(false.B)
  val validReg = RegInit(false.B)
  val toggleReg = RegInit(false.B)
  val dataReg = Reg(Bits(Config.TILE_ROM_DATA_WIDTH.W))

  // The start flag is asserted when there is no valid pixel data
  val start = !pendingReg && !validReg

  // The ready flag is asserted when the decoder needs to fetch tile ROM data.
  //
  // For 4BPP tiles we only need to fetch one 64-bit word for every other row, and for 8BPP we need
  // to fetch one 64-bit to decode a row.
  val ready = io.pixelData.ready && (is8BPP || toggleReg)

  // Decode the tile ROM data
  val bits = MuxLookup(io.format, VecInit(TilemapDecoder.decode4BPP(dataReg, toggleReg)), Seq(
    Config.GFX_FORMAT_8BPP.U -> VecInit(TilemapDecoder.decode8BPP(dataReg))
  ))

  // Clear registers when starting a new request
  when(start || ready) {
    pendingReg := true.B
    validReg := false.B
    toggleReg := false.B
  }

  // Update the state when there is valid tile ROM data
  when(io.rom.fire) {
    pendingReg := false.B
    validReg := true.B
    dataReg := io.rom.bits
  }

  // Update the toggle register when a request is received
  when(io.pixelData.fire) { toggleReg := !toggleReg }

  // Outputs
  io.rom.ready := io.rom.valid && pendingReg
  io.pixelData.valid := validReg
  io.pixelData.bits := bits

  printf(p"TilemapDecoder(start: $start, ready: $ready, pending: $pendingReg, valid: $validReg, toggle: $toggleReg, romReady: ${ io.rom.ready }, romValid: ${ io.rom.valid }, pixReady: ${ io.pixelData.ready }, pixValid: ${ io.pixelData.valid }, data: 0x${ Hexadecimal(dataReg) })\n")
}

object TilemapDecoder {
  /**
   * Decodes 8x8x4 tiles (i.e. 32 bits per row)
   *
   * @param data The 64-bit tile ROM data.
   * @param toggle A flag indicating whether to decode the lower or upper 32 bits of the word.
   */
  private def decode4BPP(data: Bits, toggle: Bool): Seq[Bits] = {
    val bits = Mux(toggle, data.tail(Config.TILE_ROM_DATA_WIDTH / 2), data.head(Config.TILE_ROM_DATA_WIDTH / 2))
    Seq(0, 1, 2, 3, 4, 5, 6, 7)
      // Decode data into nibbles
      .reverseMap(Util.decode(bits, 8, 4).apply)
      // Pad nibbles into 8-bit pixels
      .map(_.pad(8))
  }

  /**
   * Decodes 8x8x8 tiles (i.e. 64 bits per row)
   *
   * @param data The 64-bit tile ROM data.
   */
  private def decode8BPP(data: Bits): Seq[Bits] =
    Seq(2, 0, 3, 1, 6, 4, 7, 5, 10, 8, 11, 9, 14, 12, 15, 13)
      // Decode data into nibbles
      .reverseMap(Util.decode(data, 16, 4).apply)
      // Join high/low nibbles into 8-bit pixels
      .grouped(2).map(Cat(_)).toSeq
}
