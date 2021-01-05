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

/** The layer pipeline renders a tilemap layer. */
class LayerPipeline extends Module {
  val io = IO(new Bundle {
    /** Layer index */
    val layerIndex = Input(UInt(2.W))
    /** Previous layer priority value */
    val lastLayerPriority = Input(UInt(Config.PRIO_WIDTH.W))
    /** Layer info port */
    val layerInfo = DeqIO(new Layer)
    /** Tile info port */
    val tileInfo = DeqIO(new Tile)
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
  val updateTileInfo = Wire(Bool())
  val colCounterEnable = Wire(Bool())
  val readFifo = Wire(Bool())

  // Registers
  val layerInfoReg = RegEnable(io.layerInfo.bits, io.layerInfo.valid)
  val tileInfoReg = RegEnable(io.tileInfo.bits, updateTileInfo)
  val paletteEntryReg = Reg(new PaletteEntry)

  // Tile PISOs
  val smallTilePiso = Module(new PISO(Bits(Config.SMALL_TILE_BPP.W), Config.SMALL_TILE_SIZE))
  smallTilePiso.io.wr := readFifo
  smallTilePiso.io.din := VecInit(LayerPipeline.decodeSmallTile(io.pixelData.bits))
  val largeTilePiso = Module(new PISO(Bits(Config.LARGE_TILE_BPP.W), Config.LARGE_TILE_SIZE))
  largeTilePiso.io.wr := readFifo
  largeTilePiso.io.din := VecInit(LayerPipeline.decodeLargeTile(io.pixelData.bits))

  // Set PISO flags
  val pisoEmpty = Mux(layerInfoReg.smallTile, smallTilePiso.io.isEmpty, largeTilePiso.io.isEmpty)
  val pisoAlmostEmpty = Mux(layerInfoReg.smallTile, smallTilePiso.io.isAlmostEmpty, largeTilePiso.io.isAlmostEmpty)

  // Set number of columns/rows/tiles
  val numCols = Mux(layerInfoReg.smallTile, Config.SMALL_TILE_NUM_COLS.U, Config.LARGE_TILE_NUM_COLS.U)
  val numRows = Mux(layerInfoReg.smallTile, Config.SMALL_TILE_NUM_ROWS.U, Config.LARGE_TILE_NUM_ROWS.U)

  // Counters
  val (x, xWrap) = Counter.static(Config.SMALL_TILE_SIZE, enable = !pisoEmpty)
  val (y, yWrap) = Counter.static(Config.SMALL_TILE_SIZE, enable = xWrap)
  val (miniTileX, miniTileXWrap) = Counter.static(2, enable = xWrap && yWrap)
  val (miniTileY, miniTileYWrap) = Counter.static(2, enable = miniTileXWrap)
  val (col, colWrap) = Counter.dynamic(numCols, enable = colCounterEnable)
  val (row, rowWrap) = Counter.dynamic(numRows, enable = colWrap)

  // Set tile done flag
  val smallTileDone = !pisoEmpty && xWrap && yWrap
  val largeTileDone = !pisoEmpty && xWrap && yWrap && miniTileXWrap && miniTileYWrap
  val tileDone = Mux(layerInfoReg.smallTile, smallTileDone, largeTileDone)

  // Pixel position
  val pixelPos = Vec2(x, y)

  // Tile position
  val tilePos = {
    val x = Mux(layerInfoReg.smallTile, col ## 0.U(3.W), col ## miniTileX ## 0.U(3.W))
    val y = Mux(layerInfoReg.smallTile, row ## 0.U(3.W), row ## miniTileY ## 0.U(3.W))
    Vec2(x, y)
  }

  // Tile offset
  val scrollPos = {
    val offset = layerInfoReg.scroll + Layer.magicOffset(io.layerIndex)
    val x = Mux(layerInfoReg.smallTile, offset.x(2, 0), offset.x(3, 0))
    val y = Mux(layerInfoReg.smallTile, offset.y(2, 0), offset.y(3, 0))
    Vec2(x, y)
  }

  // Pixel position pipeline
  val stage0Pos = pixelPos + tilePos - scrollPos
  val stage1Pos = RegNext(stage0Pos)
  val stage2Pos = RegNext(stage1Pos)

  // The FIFO can only be read when it is not empty and should be read if the PISO is empty or will
  // be empty next clock cycle. Since the pipeline after the FIFO has no backpressure, and can
  // accommodate data every clock cycle, this will be the case if the PISO counter is one.
  readFifo := io.pixelData.valid && (pisoEmpty || pisoAlmostEmpty)

  // The tile info should be updated when we read a new tile from the FIFO, this can be in
  // either of two cases. First, when the counters are at 0 (no data yet) and the first pixels
  // arrive, second, when a tile finishes and the data for the second tile is already there.
  //
  // This is to achieve maximum efficiency of the pipeline. While there are tile to draw we burst
  // them from memory into the pipeline.
  val updateSmallTileInfo = readFifo && ((x === 0.U && y === 0.U) || (xWrap && yWrap))
  val updateLargeTileInfo = readFifo && ((x === 0.U && y === 0.U && miniTileX === 0.U && miniTileY === 0.U) || (xWrap && yWrap && miniTileXWrap && miniTileYWrap))
  updateTileInfo := Mux(layerInfoReg.smallTile, updateSmallTileInfo, updateLargeTileInfo)

  // Set column counter enable
  // FIXME: refactor this logic
  when(!(xWrap && yWrap)) {
    colCounterEnable := false.B
  }.elsewhen(layerInfoReg.smallTile) {
    colCounterEnable := true.B
  }.elsewhen(miniTileXWrap && miniTileYWrap) {
    colCounterEnable := true.B
  }.otherwise {
    colCounterEnable := false.B
  }

  // The tiles use the second 64 palettes, and use 16 colors (out of 256 possible in a palette)
  paletteEntryReg := PaletteEntry(
    1.U ## tileInfoReg.colorCode,
    Mux(layerInfoReg.smallTile, smallTilePiso.io.dout, largeTilePiso.io.dout)
  )

  // Set valid flag
  val valid = ShiftRegister(!pisoEmpty, 2, false.B, true.B)

  // Set done flag
  val done = ShiftRegister(tileDone, 2, false.B, true.B)

  // Calculate priority
  val priorityReadAddr = stage1Pos.x(Config.FRAME_BUFFER_ADDR_WIDTH_X - 1, 0) ##
                         stage1Pos.y(Config.FRAME_BUFFER_ADDR_WIDTH_Y - 1, 0)
  val priorityReadData = io.priority.read.dout
  val priorityWriteData = ShiftRegister(tileInfoReg.priority, 2)

  // The current pixel has priority if it has more priority than the previous pixel. Otherwise, if
  // the pixel priorities are the same then it depends on the layer priorities.
  val hasPriority = (priorityWriteData > priorityReadData) ||
                    (priorityWriteData === priorityReadData && layerInfoReg.priority >= io.lastLayerPriority)

  // Calculate visibility
  val visible = Util.between(stage2Pos.x, 0 until Config.SCREEN_WIDTH) &&
                Util.between(stage2Pos.y, 0 until Config.SCREEN_HEIGHT)

  // Calculate frame buffer data
  //
  // The transparency flag must be delayed by one cycle, as for the colors (since the colors come
  // from the palette RAM they arrive one cycle later).
  val frameBufferWrite = valid && hasPriority && !RegNext(paletteEntryReg.isTransparent) && visible
  val frameBufferAddr = stage2Pos.x(Config.FRAME_BUFFER_ADDR_WIDTH_X - 1, 0) ##
                        stage2Pos.y(Config.FRAME_BUFFER_ADDR_WIDTH_Y - 1, 0)

  // Outputs
  io.layerInfo.ready := true.B
  io.tileInfo.ready := updateTileInfo
  io.pixelData.ready := readFifo
  io.paletteRam.rd := true.B
  io.paletteRam.addr := paletteEntryReg.asUInt
  io.priority.read.rd := true.B
  io.priority.read.addr := priorityReadAddr
  io.priority.write.wr := frameBufferWrite
  io.priority.write.addr := frameBufferAddr
  io.priority.write.mask := 0.U
  io.priority.write.din := priorityWriteData
  io.frameBuffer.wr := frameBufferWrite
  io.frameBuffer.addr := frameBufferAddr
  io.frameBuffer.mask := 0.U
  io.frameBuffer.din := io.paletteRam.dout
  io.done := done
}

object LayerPipeline {
  /**
   * Decodes a small tile from the given pixel data.
   *
   * Small tile pixels are encoded as 8-bit words.
   *
   * @param data The pixel data.
   */
  def decodeSmallTile(data: Bits): Seq[Bits] =
    Seq(3, 1, 2, 0, 7, 5, 6, 4, 11, 9, 10, 8, 15, 13, 14, 12)
      .map(Util.decode(data, 16, 4).apply)
      .grouped(2)
      .map(Cat(_))
      .toSeq

  /**
   * Decodes a small tile from the given pixel data.
   *
   * Large tile pixels are encoded as 4-bit words.
   *
   * @param data The pixel data.
   */
  def decodeLargeTile(data: Bits): Seq[Bits] =
    Seq(1, 0, 3, 2, 5, 4, 7, 6, 9, 8, 11, 10, 13, 12, 15, 14)
      .map(Util.decode(data, 16, 4).apply)
}
