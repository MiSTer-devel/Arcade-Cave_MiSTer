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
import axon.util.Counter
import cave.Config
import cave.types._
import chisel3._
import chisel3.util._

/** The layer processor is responsible for rendering the tilemap layers. */
class LayerProcessor extends Module {
  val io = IO(new Bundle {
    /** Game config port */
    val gameConfig = Input(GameConfig())
    /** Start flag */
    val start = Input(Bool())
    /** Done flag */
    val done = Output(Bool())
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
  })

  // States
  object State {
    val idle :: regInfo :: setParams :: waitRam :: working :: waitPipeline :: Nil = Enum(6)
  }

  // Wires
  val effectiveRead = Wire(Bool())
  val updateTileInfo = Wire(Bool())
  val pipelineReady = Wire(Bool())
  val pipelineDone = Wire(Bool())

  // Registers
  val stateReg = RegInit(State.idle)
  val layerInfoReg = RegEnable(Layer.decode(io.layerRegs), stateReg === State.regInfo)
  val tileInfoReg = RegEnable(Tile.decode(io.layerRam.dout), updateTileInfo)
  val tileInfoTakenReg = RegInit(false.B)
  val burstTodoReg = RegInit(false.B)
  val burstReadyReg = RegInit(false.B)
  val lastLayerPriorityReg = RegInit(0.U)

  // Tile FIFO
  val tileFifo = Module(new TileFIFO)

  // Columns/rows/tiles
  val numCols = Mux(layerInfoReg.smallTile, Config.SMALL_TILE_NUM_COLS.U, Config.LARGE_TILE_NUM_COLS.U)
  val numRows = Mux(layerInfoReg.smallTile, Config.SMALL_TILE_NUM_ROWS.U, Config.LARGE_TILE_NUM_ROWS.U)
  val numTiles = Mux(layerInfoReg.smallTile, Config.SMALL_TILE_NUM_TILES.U, Config.LARGE_TILE_NUM_TILES.U)

  // Counters
  val (col, colWrap) = Counter.dynamic(numCols, enable = effectiveRead, reset = stateReg === State.idle)
  val (row, rowWrap) = Counter.dynamic(numRows, enable = colWrap, reset = stateReg === State.idle)
  val (_, tileWrap) = Counter.dynamic(numTiles, enable = pipelineDone, reset = stateReg === State.idle)

  // Layer pipeline
  val layerPipeline = withReset(stateReg === State.idle) { Module(new LayerPipeline) }
  layerPipeline.io.gameConfig <> io.gameConfig
  layerPipeline.io.layerIndex := io.layerIndex
  layerPipeline.io.lastLayerPriority := lastLayerPriorityReg
  layerPipeline.io.layerInfo.bits := layerInfoReg
  layerPipeline.io.layerInfo.valid := stateReg === State.setParams
  layerPipeline.io.tileInfo.bits := tileInfoReg
  pipelineReady := layerPipeline.io.tileInfo.ready
  layerPipeline.io.tileInfo.valid := updateTileInfo
  layerPipeline.io.pixelData <> tileFifo.io.deq
  layerPipeline.io.paletteRam <> io.paletteRam
  layerPipeline.io.priority <> io.priority
  layerPipeline.io.frameBuffer <> io.frameBuffer
  pipelineDone := layerPipeline.io.done

  // Control signals
  val tileBurstRead = burstTodoReg && burstReadyReg && tileFifo.io.enq.ready
  val lastBurst = effectiveRead && colWrap && rowWrap
  effectiveRead := tileBurstRead && !io.tileRom.waitReq
  updateTileInfo := stateReg === State.working && tileInfoTakenReg

  // Set first tile index
  val firstTileIndex = {
    val offset = layerInfoReg.scroll + Layer.magicOffset(io.layerIndex)
    Mux(layerInfoReg.smallTile,
      (offset.y(8, 3) ## 0.U(6.W)) + offset.x(8, 3),
      (offset.y(8, 4) ## 0.U(5.W)) + offset.x(8, 4)
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
    Mux(layerInfoReg.smallTile, small, large)
  }

  // Set tile ROM address
  val tileRomAddr = Mux(layerInfoReg.smallTile,
    tileInfoReg.code * Config.SMALL_TILE_BYTE_SIZE.U,
    tileInfoReg.code * Config.LARGE_TILE_BYTE_SIZE.U
  )

  // Set tile ROM burst length
  val tileRomBurstLength = Mux(layerInfoReg.smallTile,
    Config.SMALL_TILE_SIZE.U,
    Config.LARGE_TILE_SIZE.U
  )

  // Enqueue valid tile ROM data into the tile FIFO
  when(io.tileRom.valid) {
    tileFifo.io.enq.enq(io.tileRom.dout)
  } otherwise {
    tileFifo.io.enq.noenq()
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
    burstTodoReg := false.B
  }.elsewhen(updateTileInfo) {
    burstTodoReg := true.B
  }.elsewhen(effectiveRead) {
    burstTodoReg := false.B
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
      when(io.start) { stateReg := State.regInfo }
    }

    // Latch registers
    is(State.regInfo) { stateReg := State.setParams }

    // Set parameters
    is(State.setParams) { stateReg := Mux(layerInfoReg.disable, State.idle, State.waitRam) }

    // Wait for RAM
    is(State.waitRam) { stateReg := State.working }

    // Copy the tiles
    is(State.working) {
      when(lastBurst) { stateReg := State.waitPipeline }
    }

    // Wait for the pipeline
    is(State.waitPipeline) {
      when(tileWrap) { stateReg := State.idle }
    }
  }

  // Outputs
  io.layerRam.rd := true.B
  io.layerRam.addr := layerRamAddr
  io.tileRom.rd := tileBurstRead
  io.tileRom.addr := tileRomAddr
  io.tileRom.burstLength := tileRomBurstLength
  io.done := RegNext(tileWrap) // TODO: Does this signal need to be delayed?
}
