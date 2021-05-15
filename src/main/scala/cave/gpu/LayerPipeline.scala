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
    /** Game config port */
    val gameConfig = Input(GameConfig())
    /** Options port */
    val options = OptionsIO()
    /** Layer index */
    val layerIndex = Input(UInt(2.W))
    /** Previous layer priority value */
    val lastLayerPriority = Input(UInt(Config.PRIO_WIDTH.W))
    /** Layer info port */
    val layerInfo = DeqIO(new Layer)
    /** Tile info port */
    val tileInfo = DeqIO(new Tile)
    /** Pixel data port */
    val pixelData = DeqIO(Vec(Config.SMALL_TILE_SIZE, Bits(Config.TILE_MAX_BPP.W)))
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
  val pixelDataReady = Wire(Bool())

  // Registers
  val layerInfoReg = RegEnable(io.layerInfo.bits, io.layerInfo.valid)
  val tileInfoReg = RegEnable(io.tileInfo.bits, updateTileInfo)
  val paletteEntryReg = Reg(new PaletteEntry)

  // The PISO buffers the pixels to be copied to the frame buffer
  val piso = Module(new PISO(Config.SMALL_TILE_SIZE, Bits(Config.TILE_MAX_BPP.W)))
  piso.io.wr := io.pixelData.fire()
  piso.io.din := io.pixelData.bits

  // Set PISO flags
  val pisoEmpty = piso.io.isEmpty
  val pisoAlmostEmpty = piso.io.isAlmostEmpty

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
  val pixelPos = UVec2(x, y)

  // Tile position
  val tilePos = {
    val x = Mux(layerInfoReg.smallTile, col ## 0.U(3.W), col ## miniTileX ## 0.U(3.W))
    val y = Mux(layerInfoReg.smallTile, row ## 0.U(3.W), row ## miniTileY ## 0.U(3.W))
    UVec2(x, y)
  }

  // Tile offset
  val scrollPos = {
    val offset = layerInfoReg.scroll + Layer.magicOffset(io.layerIndex, layerInfoReg.smallTile)
    val x = Mux(layerInfoReg.smallTile, offset.x(2, 0), offset.x(3, 0))
    val y = Mux(layerInfoReg.smallTile, offset.y(2, 0), offset.y(3, 0))
    UVec2(x, y)
  }

  // Pixel position pipeline
  val stage0Pos = pixelPos + tilePos - scrollPos
  val stage1Pos = RegNext(stage0Pos)
  val stage2Pos = RegNext(stage1Pos)

  // The FIFO can only be read when it is not empty and should be read if the PISO is empty or will
  // be empty next clock cycle. Since the pipeline after the FIFO has no backpressure, and can
  // accommodate data every clock cycle, this will be the case if the PISO counter is one.
  pixelDataReady := io.pixelData.valid && (pisoEmpty || pisoAlmostEmpty)

  // The tile info should be updated when we read a new tile from the FIFO, this can be in
  // either of two cases. First, when the counters are at 0 (no data yet) and the first pixels
  // arrive, second, when a tile finishes and the data for the second tile is already there.
  //
  // This is to achieve maximum efficiency of the pipeline. While there are tile to draw we burst
  // them from memory into the pipeline.
  val updateSmallTileInfo = pixelDataReady && ((x === 0.U && y === 0.U) || (xWrap && yWrap))
  val updateLargeTileInfo = pixelDataReady && ((x === 0.U && y === 0.U && miniTileX === 0.U && miniTileY === 0.U) || (xWrap && yWrap && miniTileXWrap && miniTileYWrap))
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
  paletteEntryReg := PaletteEntry(1.U ## tileInfoReg.colorCode, piso.io.dout)
  val paletteRamAddr = paletteEntryReg.toAddr(io.gameConfig.numColors)

  // Set delayed valid/done shift registers
  val validReg = ShiftRegister(!pisoEmpty, 2, false.B, true.B)
  val doneReg = ShiftRegister(tileDone, 2, false.B, true.B)

  // Set priority data
  val priorityReadAddr = GPU.transformAddr(stage1Pos, io.options.flip, io.options.rotate)
  val priorityReadData = io.priority.read.dout
  val priorityWriteData = ShiftRegister(tileInfoReg.priority, 3)

  // The current pixel has priority if it has more priority than the previous pixel. Otherwise, if
  // the pixel priorities are the same then it depends on the layer priorities.
  val hasPriority = (tileInfoReg.priority > priorityReadData) ||
                    (tileInfoReg.priority === priorityReadData && layerInfoReg.priority >= io.lastLayerPriority)

  // The transparency flag must be delayed by one cycle, since the colors come from the palette RAM
  // they arrive one cycle later.
  val visible = hasPriority && GPU.isVisible(stage2Pos) && !RegNext(paletteEntryReg.isTransparent)

  // Set frame buffer signals
  val frameBufferWrite = RegNext(validReg && visible)
  val frameBufferAddr = RegNext(GPU.transformAddr(stage2Pos, io.options.flip, io.options.rotate))
  val frameBufferData = RegNext(io.paletteRam.dout)

  // Outputs
  io.layerInfo.ready := true.B
  io.tileInfo.ready := updateTileInfo
  io.pixelData.ready := pixelDataReady
  io.paletteRam.rd := true.B
  io.paletteRam.addr := paletteRamAddr
  io.priority.read.rd := true.B
  io.priority.read.addr := priorityReadAddr
  io.priority.write.wr := frameBufferWrite
  io.priority.write.addr := frameBufferAddr
  io.priority.write.mask := 0.U
  io.priority.write.din := priorityWriteData
  io.frameBuffer.wr := frameBufferWrite
  io.frameBuffer.addr := frameBufferAddr
  io.frameBuffer.mask := 0.U
  io.frameBuffer.din := frameBufferData
  io.done := doneReg
}
