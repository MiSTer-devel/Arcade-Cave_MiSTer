/*
 *    __   __     __  __     __         __
 *   /\ "-.\ \   /\ \/\ \   /\ \       /\ \
 *   \ \ \-.  \  \ \ \_\ \  \ \ \____  \ \ \____
 *    \ \_\\"\_\  \ \_____\  \ \_____\  \ \_____\
 *     \/_/ \/_/   \/_____/   \/_____/   \/_____/
 *    ______     ______       __     ______     ______     ______
 *   /\  __ \   /\  == \     /\ \   /\  ___\   /\  ___\   /\__  _\
 *   \ \ \/\ \  \ \  __<    _\_\ \  \ \  __\   \ \ \____  \/_/\ \/
 *    \ \_____\  \ \_____\ /\_____\  \ \_____\  \ \_____\    \ \_\
 *     \/_____/   \/_____/ \/_____/   \/_____/   \/_____/     \/_/
 *
 *  https://joshbassett.info
 *  https://twitter.com/nullobject
 *  https://github.com/nullobject
 *
 *  Copyright (c) 2020 Josh Bassett
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
 */

package cave.gpu

import axon.Util
import axon.mem._
import axon.types._
import axon.util.Counter
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
    val layerInfo = DeqIO(Bits(Config.LAYER_REGS_GPU_DATA_WIDTH.W))
    /** Tile info port */
    val tileInfo = DeqIO(Bits(Config.LAYER_RAM_GPU_DATA_WIDTH.W))
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

  // Decode layer info
  val layerInfo = Layer.decode(io.layerInfo.bits)

  // Decode tile info
  val tileInfo = Tile.decode(io.tileInfo.bits)

  // Wires
  val updateTileInfo = Wire(Bool())
  val updateScreenTileCounter = Wire(Bool())
  val pisoEmpty = Wire(Bool())
  val pisoAlmostEmpty = Wire(Bool())
  val readFifo = Wire(Bool())

  // Registers
  val layerInfoReg = RegEnable(layerInfo, io.layerInfo.valid)
  val tileInfoReg = RegEnable(tileInfo, updateTileInfo)
  val paletteReg = Reg(new PaletteColorSelect)
  val smallPisoReg = Reg(Vec(Config.SMALL_TILE_SIZE, UInt(Config.SMALL_TILE_BPP.W)))
  val largePisoReg = Reg(Vec(Config.LARGE_TILE_SIZE, UInt(Config.LARGE_TILE_BPP.W)))
  val pisoCounterReg = RegInit(0.U)

  // Set number of columns/rows/tiles
  val numCols = Mux(layerInfo.smallTile, Config.SMALL_TILE_NUM_COLS.U, Config.LARGE_TILE_NUM_COLS.U)
  val numRows = Mux(layerInfo.smallTile, Config.SMALL_TILE_NUM_ROWS.U, Config.LARGE_TILE_NUM_ROWS.U)

  // Counters
  val (x, xWrap) = Counter.static(8, enable = !pisoEmpty)
  val (y, yWrap) = Counter.static(8, enable = xWrap)
  val (miniTileX, miniTileXWrap) = Counter.static(2, enable = xWrap && yWrap)
  val (miniTileY, miniTileYWrap) = Counter.static(2, enable = miniTileXWrap)
  val (col, colWrap) = Counter.dynamic(numCols, enable = updateScreenTileCounter)
  val (row, rowWrap) = Counter.dynamic(numRows, enable = colWrap)

  // Set tile done flag
  val smallTileDone = xWrap && yWrap && !pisoEmpty
  val largeTileDone = smallTileDone && miniTileXWrap && miniTileYWrap
  val tileDone = Mux(layerInfo.smallTile, smallTileDone, largeTileDone)

  // Tile position
  val tilePos = {
    val x = Mux(layerInfo.smallTile,
      col ## 0.U(3.W),
      (col ## 0.U(4.W)) + (miniTileX ## 0.U(3.W))
    )
    val y = Mux(layerInfo.smallTile,
      row ## 0.U(3.W),
      (row ## 0.U(4.W)) + (miniTileY ## 0.U(3.W))
    )
    Vec2(x, y)
  }

  // Tile offset
  val tileOffset = {
    val xMagicOffset = MuxLookup(io.layerIndex, 0.U, Seq(0.U -> 0x6b.U, 1.U -> 0x6c.U, 2.U -> 0x75.U))
    val yMagicOffset = 17.U
    val xScroll = layerInfo.scroll.x + xMagicOffset
    val yScroll = layerInfo.scroll.y + yMagicOffset
    val x = Mux(layerInfo.smallTile, xScroll(2, 0), xScroll(3, 0))
    val y = Mux(layerInfo.smallTile, yScroll(2, 0), yScroll(3, 0))
    Vec2(x, y)
  }

  // Pixel position
  val pixelPos = Vec2(x, y)

  // Pixel position pipeline
  val stage0Pos = tilePos + pixelPos - tileOffset
  val stage1Pos = RegNext(stage0Pos)
  val stage2Pos = RegNext(stage1Pos)

  // Update PISO counter register
  when(readFifo) {
    pisoCounterReg := Mux(layerInfo.smallTile, Config.SMALL_TILE_SIZE.U, Config.LARGE_TILE_SIZE.U)
  }.elsewhen(!pisoEmpty) {
    pisoCounterReg := pisoCounterReg - 1.U
  }

  // Decode pixel data into the PISO
  when(readFifo) {
    smallPisoReg := VecInit(LayerPipeline.decodeSmallTile(io.pixelData.bits))
    largePisoReg := VecInit(LayerPipeline.decodeLargeTile(io.pixelData.bits))
  }.otherwise {
    smallPisoReg := smallPisoReg.tail :+ smallPisoReg.head
    largePisoReg := largePisoReg.tail :+ largePisoReg.head
  }

  // Set PISO empty flags
  pisoEmpty := pisoCounterReg === 0.U
  pisoAlmostEmpty := pisoCounterReg === 1.U

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
  updateTileInfo := Mux(layerInfo.smallTile, updateSmallTileInfo, updateLargeTileInfo)

  // Set update screen tile counter flag
  // FIXME: refactor this logic
  when(!(xWrap && yWrap)) {
    updateScreenTileCounter := false.B
  }.elsewhen(layerInfo.smallTile) {
    updateScreenTileCounter := true.B
  }.elsewhen(miniTileXWrap && miniTileYWrap) {
    updateScreenTileCounter := true.B
  }.otherwise {
    updateScreenTileCounter := false.B
  }

  // The tiles use the second 64 palettes, and use 16 colors (out of 256 possible in a palette)
  paletteReg := PaletteColorSelect(
    1.U ## tileInfoReg.colorCode,
    Mux(layerInfo.smallTile, smallPisoReg.head, largePisoReg.head)
  )

  // The CAVE first-generation hardware handles transparency the following way:
  //
  // If the color code (palette index) is 0 the pixel is transparent. This results in 15 usable
  // colors for a tile. Even if the color at the first index of the palette is not zero, the pixel
  // still is transparent.
  //
  // It is difficult to understand why CAVE didn't use the MSB bit of the 16 bit color word to
  // indicate transparency. This would allow for 16 colors out of 2^15 colors for each tile instead
  // of 15 colors of 2^15. With the cave CV1000 (SH3) hardware, they use the MSB bit of the 16-bit
  // word as transparency bit while the colors remain RGB555.
  //
  // One wonders why they didn't do this on first-generation hardware. The transparency info must be
  // delayed by one cycle, as for the colors (since the colors come from the palette RAM (BRAM) they
  // arrive one cycle later).
  // FIXME: move to PaletteEntry class.
  val isTransparent = RegNext(paletteReg.colorIndex === 0.U)

  // Set valid flag
  val valid = ShiftRegister(!pisoEmpty, 2, false.B, true.B)

  // Set done flag
  val done = ShiftRegister(tileDone, 2, false.B, true.B)

  // Calculate priority
  val priorityReadAddr = stage1Pos.x(Config.FRAME_BUFFER_ADDR_WIDTH_X - 1, 0) ##
                         stage1Pos.y(Config.FRAME_BUFFER_ADDR_WIDTH_Y - 1, 0)
  val priorityWriteData = ShiftRegister(tileInfoReg.priority, 2)

  // Calculate the pixel priority
  //
  // The current pixel has priority if it has more priority than the previous pixel. Otherwise, if
  // the pixel priorities are the same then it depends on the layer priorities.
  val hasPriority = (priorityWriteData > io.priority.read.dout) ||
                    (priorityWriteData === io.priority.read.dout && layerInfo.priority >= io.lastLayerPriority)

  // Calculate visibility
  val visible = Util.between(stage2Pos.x, 0 until Config.SCREEN_WIDTH) &&
                Util.between(stage2Pos.y, 0 until Config.SCREEN_HEIGHT)

  // Calculate frame buffer data
  val frameBufferWrite = valid && hasPriority && !isTransparent && visible
  val frameBufferAddr = stage2Pos.x(Config.FRAME_BUFFER_ADDR_WIDTH_X - 1, 0) ##
                        stage2Pos.y(Config.FRAME_BUFFER_ADDR_WIDTH_Y - 1, 0)
  val frameBufferData = GPU.decodePaletteData(io.paletteRam.dout)

  // Outputs
  io.layerInfo.ready := true.B
  io.tileInfo.ready := updateTileInfo
  io.pixelData.ready := readFifo
  io.paletteRam.rd := true.B
  io.paletteRam.addr := paletteReg.asUInt
  io.priority.read.rd := true.B
  io.priority.read.addr := priorityReadAddr
  io.priority.write.wr := frameBufferWrite
  io.priority.write.addr := frameBufferAddr
  io.priority.write.mask := 0.U
  io.priority.write.din := priorityWriteData
  io.frameBuffer.wr := frameBufferWrite
  io.frameBuffer.addr := frameBufferAddr
  io.frameBuffer.mask := 0.U
  io.frameBuffer.din := frameBufferData.asUInt
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
