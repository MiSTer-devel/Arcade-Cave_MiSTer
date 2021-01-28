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
import axon.mem._
import axon.types._
import axon.util.{Counter, PISO}
import cave.Config
import cave.types._
import chisel3._
import chisel3.util._

/** The sprite blitter copies a sprite to the frame buffer. */
class SpriteBlitter extends Module {
  val io = IO(new Bundle {
    /** Game config port */
    val gameConfig = Input(GameConfig())
    /** Options port */
    val options = OptionsIO()
    /** Sprite info port */
    val spriteInfo = DeqIO(new Sprite)
    /** Pixel data port */
    val pixelData = DeqIO(Bits(Config.TILE_ROM_DATA_WIDTH.W))
    /** Palette RAM port */
    val paletteRam = ReadMemIO(Config.PALETTE_RAM_GPU_ADDR_WIDTH, Config.PALETTE_RAM_GPU_DATA_WIDTH)
    /** Priority port */
    val priority = new PriorityIO
    /** Frame buffer port */
    val frameBuffer = WriteMemIO(Config.FRAME_BUFFER_ADDR_WIDTH, Config.FRAME_BUFFER_DATA_WIDTH)
    /** Done flag */
    val done = Output(Bool())
  })

  // Wires
  val updateSpriteInfo = Wire(Bool())
  val readFifo = Wire(Bool())

  // Registers
  val spriteInfoReg = RegEnable(io.spriteInfo.bits, updateSpriteInfo)
  val paletteEntryReg = Reg(new PaletteEntry)

  // Tile PISO
  val tilePiso = Module(new PISO(Bits(Config.LARGE_TILE_BPP.W), Config.LARGE_TILE_SIZE))
  tilePiso.io.wr := readFifo
  tilePiso.io.din := SpriteBlitter.decodeTile(io.gameConfig.spriteFormat, io.pixelData.bits)

  // Set PISO flags
  val pisoEmpty = tilePiso.io.isEmpty
  val pisoAlmostEmpty = tilePiso.io.isAlmostEmpty

  // Counters
  val (x, xWrap) = Counter.dynamic(spriteInfoReg.size.x, enable = !pisoEmpty)
  val (y, yWrap) = Counter.dynamic(spriteInfoReg.size.y, enable = xWrap)

  // Set sprite done flag
  val spriteDone = xWrap && yWrap && !pisoEmpty

  // Pixel position
  val pixelPos = {
    val xPos = Mux(spriteInfoReg.flipX, spriteInfoReg.size.x - x - 1.U, x)
    val yPos = Mux(spriteInfoReg.flipY, spriteInfoReg.size.y - y - 1.U, y)
    SVec2(xPos.asSInt, yPos.asSInt)
  }

  // Pixel position pipeline
  val stage0Pos = spriteInfoReg.pos + pixelPos
  val stage1Pos = RegNext(stage0Pos)
  val stage2Pos = RegNext(stage1Pos)

  // The FIFO can only be read when it is not empty and should be read if the PISO is empty or will
  // be empty next clock cycle. Since the pipeline after the FIFO has no backpressure, and can
  // accommodate data every clock cycle, this will be the case if the PISO counter is one.
  readFifo := io.pixelData.valid && (pisoEmpty || pisoAlmostEmpty)

  // The sprite info should be updated when we read a new sprite from the FIFO, this can be in
  // either of two cases. First, when the counters are at 0 (no data yet) and the first pixels
  // arrive, second, when a sprite finishes and the data for the second sprite is already there.
  //
  // This is to achieve maximum efficiency of the pipeline. While there are sprites to draw we burst
  // them from memory into the pipeline. Max one 16x16 tile in advance, so there are at most 2 16x16
  // tiles in the pipeline at any given time.
  //
  // When there is space in the pipeline for a 16x16 tile and there are sprites to draw, a tile will
  // be bursted from memory into the pipeline. A 16x16 tile is 16 bytes since a byte encodes two
  // pixel colors on nibbles, these are 16 color sprites.
  updateSpriteInfo := readFifo && ((x === 0.U && y === 0.U) || (xWrap && yWrap))

  // The sprites use the first 64 palettes, and use 16 colors (out of 256 possible in a palette)
  paletteEntryReg := PaletteEntry(spriteInfoReg.colorCode, tilePiso.io.dout)

  // Set valid/done flags
  val valid = ShiftRegister(!pisoEmpty, 2, false.B, true.B)
  val done = ShiftRegister(spriteDone, 2, false.B, true.B)

  // Set priority data
  val priorityWriteData = ShiftRegister(spriteInfoReg.priority, 3)

  // The transparency flag must be delayed by one cycle, since the colors come from the palette RAM
  // they arrive one cycle later.
  val visible = GPU.isVisible(stage2Pos) && !RegNext(paletteEntryReg.isTransparent)

  // Set frame buffer signals
  val frameBufferWrite = RegNext(valid && visible)
  val frameBufferAddr = RegNext(GPU.transformAddr(stage2Pos, io.options.flip, io.options.rotate))

  // Outputs
  io.spriteInfo.ready := updateSpriteInfo
  io.pixelData.ready := readFifo
  io.paletteRam.rd := true.B
  io.paletteRam.addr := paletteEntryReg.toAddr(io.gameConfig.numColors)
  io.priority.read.rd := false.B
  io.priority.read.addr := 0.U
  io.priority.write.wr := frameBufferWrite
  io.priority.write.addr := frameBufferAddr
  io.priority.write.mask := 0.U
  io.priority.write.din := priorityWriteData
  io.frameBuffer.wr := frameBufferWrite
  io.frameBuffer.addr := frameBufferAddr
  io.frameBuffer.mask := 0.U
  io.frameBuffer.din := RegNext(io.paletteRam.dout)
  io.done := done

  printf(p"SpriteBlitter(paletteEntry: $paletteEntryReg, valid: $valid, visible: $visible, pos: $stage2Pos)\n")
}

object SpriteBlitter {
  /**
   * Decodes a sprite tile from the given pixel data.
   *
   * @param data The pixel data.
   * @param format The tile format.
   * @return A vector containing the decoded tile pixels.
   */
  def decodeTile(format: UInt, data: Bits): Vec[Bits] =
    MuxLookup(format, VecInit(decodeSpriteTile(data)), Seq(
      GameConfig.TILE_FORMAT_SPRITE_MSB.U -> VecInit(decodeSpriteMSBTile(data))
    ))

  private def decodeSpriteTile(data: Bits): Seq[Bits] =
    Seq(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15)
      .map(Util.decode(data, 16, 4).apply)

  private def decodeSpriteMSBTile(data: Bits): Seq[Bits] =
    Seq(3, 2, 1, 0, 7, 6, 5, 4, 11, 10, 9, 8, 15, 14, 13, 12)
      .map(Util.decode(data, 16, 4).apply)
}
