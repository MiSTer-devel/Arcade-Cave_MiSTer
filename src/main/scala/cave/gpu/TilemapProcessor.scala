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
import axon.util.Counter
import cave.Config
import cave.types._
import chisel3._
import chisel3.util._

/** The tilemap processor is responsible for rendering tilemap layers. */
class TilemapProcessor extends Module {
  val io = IO(new Bundle {
    /** Game config port */
    val gameConfig = Input(GameConfig())
    /** Options port */
    val options = OptionsIO()
    /** When the start flag is asserted, the tiles are rendered to the frame buffer */
    val start = Input(Bool())
    /** The busy flag is asserted while the processor is busy */
    val busy = Output(Bool())
    /** Layer index */
    val layerIndex = Input(UInt(Config.LAYER_INDEX_WIDTH.W))
    /** Layer registers port */
    val layerRegs = Input(Bits(Config.LAYER_REGS_GPU_DATA_WIDTH.W))
    /** Layer RAM port */
    val layerRam = ReadMemIO(Config.LAYER_RAM_GPU_ADDR_WIDTH, Config.LAYER_RAM_GPU_DATA_WIDTH)
    /** Palette RAM port */
    val paletteRam = ReadMemIO(Config.PALETTE_RAM_GPU_ADDR_WIDTH, Config.PALETTE_RAM_GPU_DATA_WIDTH)
    /** Tile ROM port */
    val tileRom = new TileRomIO
    /** Priority port */
    val priority = new PriorityIO
    /** Frame buffer port */
    val frameBuffer = WriteMemIO(Config.FRAME_BUFFER_ADDR_WIDTH, Config.FRAME_BUFFER_DATA_WIDTH)
    /** Debug port */
    val debug = Output(new Bundle {
      val idle = Bool()
      val latch = Bool()
      val setParams = Bool()
      val waitRam = Bool()
      val working = Bool()
      val done = Bool()
    })
  })

  // States
  object State {
    val idle :: latch :: setParams :: waitRam :: working :: done :: Nil = Enum(6)
  }

  // Wires
  val effectiveRead = Wire(Bool())
  val updateTileInfo = Wire(Bool())
  val pipelineReady = Wire(Bool())
  val pipelineDone = Wire(Bool())

  // Registers
  val stateReg = RegInit(State.idle)
  val layerInfoReg = RegEnable(Layer.decode(io.layerRegs), stateReg === State.latch)
  val tileInfoReg = RegEnable(Tile.decode(io.layerRam.dout, layerInfoReg.tileSize), updateTileInfo)
  val tileInfoTakenReg = RegInit(false.B)
  val burstPendingReg = RegInit(false.B)
  val burstReadyReg = RegInit(false.B)
  val lastLayerPriorityReg = RegInit(0.U)

  // The FIFO buffers the raw data read from the tile ROM. It can store up to 64 words, which is
  // enough room for two 16x16x8BPP tiles.
  //
  // The queue is configured in show-ahead mode, which means there will be valid output as soon as
  // an element has been written to the queue.
  val fifo = Module(new Queue(Bits(Config.TILE_ROM_DATA_WIDTH.W), 64, flow = true))

  // Columns/rows/tiles
  val numCols = Mux(layerInfoReg.tileSize, Config.LARGE_TILE_NUM_COLS.U, Config.SMALL_TILE_NUM_COLS.U)
  val numRows = Mux(layerInfoReg.tileSize, Config.LARGE_TILE_NUM_ROWS.U, Config.SMALL_TILE_NUM_ROWS.U)
  val numTiles = Mux(layerInfoReg.tileSize, Config.LARGE_TILE_NUM_TILES.U, Config.SMALL_TILE_NUM_TILES.U)

  // Counters
  val (col, colWrap) = Counter.dynamic(numCols, enable = effectiveRead, reset = stateReg === State.idle)
  val (row, rowWrap) = Counter.dynamic(numRows, enable = colWrap, reset = stateReg === State.idle)
  val (_, tileWrap) = Counter.dynamic(numTiles, enable = pipelineDone, reset = stateReg === State.idle)

  // Layer pipeline
  val layerPipeline = withReset(stateReg === State.idle) { Module(new TilemapBlitter) }
  layerPipeline.io.gameConfig <> io.gameConfig
  layerPipeline.io.options <> io.options
  layerPipeline.io.layerIndex := io.layerIndex
  layerPipeline.io.lastLayerPriority := lastLayerPriorityReg
  layerPipeline.io.layerInfo.bits := layerInfoReg
  layerPipeline.io.layerInfo.valid := stateReg === State.setParams
  layerPipeline.io.tileInfo.bits := tileInfoReg
  pipelineReady := layerPipeline.io.tileInfo.ready
  layerPipeline.io.tileInfo.valid := updateTileInfo
  layerPipeline.io.paletteRam <> io.paletteRam
  layerPipeline.io.priority <> io.priority
  layerPipeline.io.frameBuffer <> io.frameBuffer
  pipelineDone := layerPipeline.io.done

  // Set layer format
  val layerFormat = MuxLookup(io.layerIndex, io.gameConfig.layer0Format, Seq(
    1.U -> io.gameConfig.layer1Format,
    2.U -> io.gameConfig.layer2Format
  ))

  // Tile decoder
  val tileDecoder = Module(new SmallTileDecoder)
  tileDecoder.io.format := layerFormat
  tileDecoder.io.rom <> fifo.io.deq
  tileDecoder.io.pixelData <> layerPipeline.io.pixelData

  // Control signals
  val tileRomRead = burstPendingReg && burstReadyReg && fifo.io.count <= 32.U
  val lastBurst = effectiveRead && colWrap && rowWrap
  effectiveRead := tileRomRead && !io.tileRom.waitReq
  updateTileInfo := stateReg === State.working && tileInfoTakenReg

  // Set first tile index
  val firstTileIndex = {
    val offset = layerInfoReg.scroll + layerInfoReg.magicOffset(io.layerIndex)
    Mux(layerInfoReg.tileSize,
      (offset.y(8, 4) ## 0.U(5.W)) + offset.x(8, 4),
      (offset.y(8, 3) ## 0.U(6.W)) + offset.x(8, 3)
    )
  }

  // Set layer RAM address
  val layerRamAddr = {
    val x = firstTileIndex + col
    val small = {
      val y = firstTileIndex(11, 6) + row
      (y(5, 0) ## 0.U(6.W)) + x(5, 0)
    }
    val large = {
      val y = firstTileIndex(9, 5) + row
      (y(4, 0) ## 0.U(5.W)) + x(4, 0)
    }
    Mux(layerInfoReg.tileSize, large, small)
  }

  // Set tile format flags
  val tileFormat_8x8x8 = !layerInfoReg.tileSize && layerFormat === Config.GFX_FORMAT_8x8x8.U
  val tileFormat_16x16x4 = layerInfoReg.tileSize && layerFormat === Config.GFX_FORMAT_8x8x4.U
  val tileFormat_16x16x8 = layerInfoReg.tileSize && layerFormat === Config.GFX_FORMAT_8x8x8.U

  // Set tile ROM address
  val tileRomAddr = MuxCase(0.U, Seq(
    tileFormat_8x8x8 -> (tileInfoReg.code << log2Ceil(Config.TILE_SIZE_8x8x8)),
    tileFormat_16x16x4 -> (tileInfoReg.code << log2Ceil(Config.TILE_SIZE_16x16x4)),
    tileFormat_16x16x8 -> (tileInfoReg.code << log2Ceil(Config.TILE_SIZE_16x16x8))
  ))

  // Set tile ROM burst length. The burst length defaults to 8 words, because while the game is
  // booting the layer registers won't be set properly.
  val tileRomBurstLength = MuxCase(8.U, Seq(
    tileFormat_8x8x8 -> (Config.TILE_SIZE_8x8x8 / 8).U,
    tileFormat_16x16x4 -> (Config.TILE_SIZE_16x16x4 / 8).U,
    tileFormat_16x16x8 -> (Config.TILE_SIZE_16x16x8 / 8).U
  ))

  // Enqueue valid tile ROM data into the tile FIFO
  when(io.tileRom.valid) {
    fifo.io.enq.enq(io.tileRom.dout)
  } otherwise {
    fifo.io.enq.noenq()
  }

  // Toggle tile info taken register
  when(stateReg === State.idle) {
    tileInfoTakenReg := false.B
  }.elsewhen(stateReg === State.waitRam || pipelineReady) {
    tileInfoTakenReg := true.B
  }.elsewhen(updateTileInfo) {
    tileInfoTakenReg := false.B
  }

  // Toggle burst pending register
  when(stateReg === State.idle) {
    burstPendingReg := false.B
  }.elsewhen(updateTileInfo) {
    burstPendingReg := true.B
  }.elsewhen(effectiveRead) {
    burstPendingReg := false.B
  }

  // Toggle burst ready register
  when(stateReg === State.idle) {
    burstReadyReg := true.B
  }.elsewhen(effectiveRead) {
    burstReadyReg := false.B
  }.elsewhen(io.tileRom.burstDone) {
    burstReadyReg := true.B
  }

  // Set last layer priority register
  when(stateReg === State.setParams && io.layerIndex === 0.U) {
    lastLayerPriorityReg := 0.U
  }.elsewhen(stateReg === State.working && tileWrap) {
    lastLayerPriorityReg := layerInfoReg.priority
  }

  // FSM
  switch(stateReg) {
    // Wait for the start signal
    is(State.idle) {
      when(io.start) { stateReg := State.latch }
    }

    // Latch layer info
    is(State.latch) { stateReg := State.setParams }

    // Set parameters
    is(State.setParams) { stateReg := Mux(layerInfoReg.disable, State.idle, State.waitRam) }

    // Wait for RAM
    is(State.waitRam) { stateReg := State.working }

    // Copy the tiles
    is(State.working) {
      when(lastBurst) { stateReg := State.done }
    }

    // Wait for the blitter to finish
    is(State.done) {
      when(tileWrap) { stateReg := State.idle }
    }
  }

  // Outputs
  io.busy := stateReg =/= State.idle
  io.layerRam.rd := true.B
  io.layerRam.addr := layerRamAddr
  io.tileRom.rd := tileRomRead
  io.tileRom.addr := tileRomAddr
  io.tileRom.burstLength := tileRomBurstLength
  io.debug.idle := stateReg === State.idle
  io.debug.latch := stateReg === State.latch
  io.debug.setParams := stateReg === State.setParams
  io.debug.waitRam := stateReg === State.waitRam
  io.debug.working := stateReg === State.working
  io.debug.done := stateReg === State.done
}
