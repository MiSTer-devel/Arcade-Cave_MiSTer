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

package cave.gpu

import axon.mem._
import axon.types._
import axon.util.Counter
import cave.Config
import cave.types._
import chisel3._
import chisel3.util._

/**
 * The tilemap processor is responsible for rendering tilemap layers.
 *
 * @param maxCols The maximum number of columns in the tilemap.
 * @param maxRows The maximum number of rows in the tilemap.
 */
class TilemapProcessor(maxCols: Int = 40, maxRows: Int = 30) extends Module {
  val io = IO(new Bundle {
    /** Game config port */
    val gameConfig = Input(GameConfig())
    /** Options port */
    val options = OptionsIO()
    /** When the start flag is asserted, the tiles are rendered to the frame buffer */
    val start = Input(Bool())
    /** The busy flag is asserted while the processor is busy */
    val busy = Output(Bool())
    /** Layer port */
    val layer = Input(new Layer)
    /** Layer index */
    val layerIndex = Input(UInt(Config.LAYER_INDEX_WIDTH.W))
    /** Layer RAM port */
    val layerRam = new LayerRamIO
    /** Line RAM port */
    val lineRam = new LineRamIO
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
      val check = Bool()
      val load = Bool()
      val latch = Bool()
      val ready = Bool()
      val pending = Bool()
      val next = Bool()
      val done = Bool()
    })
  })

  // States
  object State {
    val idle :: check :: load :: latch :: ready :: pending :: next :: done :: Nil = Enum(8)
  }

  // Wires
  val effectiveRead = Wire(Bool())

  // Registers
  val stateReg = RegInit(State.idle)
  val tileReg = RegEnable(Tile.decode(io.layerRam.dout, io.layer.tileSize), stateReg === State.latch)
  val burstReadyReg = RegInit(false.B)
  val burstPendingReg = RegInit(false.B)
  val lastLayerPriorityReg = RegInit(0.U)

  // The FIFO buffers the raw data read from the tile ROM.
  //
  // The queue is configured in show-ahead mode, which means there will be valid output as soon as
  // an element has been written to the queue.
  val fifo = Module(new Queue(Bits(Config.TILE_ROM_DATA_WIDTH.W), Config.FIFO_DEPTH, flow = true))

  // Columns/rows
  val numCols = Mux(io.layer.tileSize, (maxCols / 2 + 1).U, (maxCols + 1).U)
  val numRows = Mux(io.layer.tileSize, (maxRows / 2 + 1).U, (maxRows + 1).U)

  // Counters
  val (col, colWrap) = Counter.dynamic(numCols, enable = stateReg === State.next)
  val (row, rowWrap) = Counter.dynamic(numRows, enable = colWrap)

  // Tilemap blitter
  val blitter = Module(new TilemapBlitter)
  blitter.io.layerIndex := io.layerIndex
  blitter.io.lastLayerPriority := lastLayerPriorityReg
  blitter.io.lineRam <> io.lineRam
  blitter.io.paletteRam <> io.paletteRam
  blitter.io.priority <> io.priority
  blitter.io.frameBuffer <> io.frameBuffer

  // Set layer format
  val layerFormat = MuxLookup(io.layerIndex, io.gameConfig.layer0Format, Seq(
    1.U -> io.gameConfig.layer1Format,
    2.U -> io.gameConfig.layer2Format
  ))

  // Tilemap decoder
  val decoder = Module(new TilemapDecoder)
  decoder.io.format := layerFormat
  decoder.io.rom <> fifo.io.deq
  decoder.io.pixelData <> blitter.io.pixelData

  // Set tile ROM read flag
  val tileRomRead = burstReadyReg && !burstPendingReg && fifo.io.count <= (Config.FIFO_DEPTH / 2).U

  // Set effective read flag
  effectiveRead := tileRomRead && !io.tileRom.waitReq

  // Set first tile index
  val firstTileIndex = {
    val offset = io.layer.scroll + io.layer.magicOffset(io.layerIndex)
    Mux(io.layer.tileSize,
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
    Mux(io.layer.tileSize, large, small)
  }

  // Set tile format flags
  val tileFormat_8x8x4 = !io.layer.tileSize && layerFormat === Config.GFX_FORMAT_4BPP.U
  val tileFormat_8x8x8 = !io.layer.tileSize && layerFormat === Config.GFX_FORMAT_8BPP.U
  val tileFormat_16x16x4 = io.layer.tileSize && layerFormat === Config.GFX_FORMAT_4BPP.U
  val tileFormat_16x16x8 = io.layer.tileSize && layerFormat === Config.GFX_FORMAT_8BPP.U

  // Set tile ROM address
  val tileRomAddr = MuxCase(0.U, Seq(
    tileFormat_8x8x8 -> (tileReg.code << log2Ceil(Config.TILE_SIZE_8x8x8)),
    tileFormat_16x16x4 -> (tileReg.code << log2Ceil(Config.TILE_SIZE_16x16x4)),
    tileFormat_16x16x8 -> (tileReg.code << log2Ceil(Config.TILE_SIZE_16x16x8))
  ))

  // Set tile ROM burst length
  val tileRomBurstLength = MuxCase(0.U, Seq(
    tileFormat_8x8x8 -> (Config.TILE_SIZE_8x8x8 / 8).U,
    tileFormat_16x16x4 -> (Config.TILE_SIZE_16x16x4 / 8).U,
    tileFormat_16x16x8 -> (Config.TILE_SIZE_16x16x8 / 8).U
  ))

  // The tilemap should not be rendered if the layer is disabled, or if the burst length is zero
  // (which occurs during startup)
  val disable = io.layer.disable || tileRomBurstLength === 0.U

  // The burst ready register is asserted when the tilemap processor is ready to burst pixel data
  when(stateReg === State.latch) {
    burstReadyReg := true.B
  }.elsewhen(effectiveRead) {
    burstReadyReg := false.B
  }

  // The burst pending register is asserted when the tilemap processor is waiting for pixel data
  when(io.tileRom.burstDone) {
    burstPendingReg := false.B
  }.elsewhen(effectiveRead) {
    burstPendingReg := true.B
  }

  // Enqueue the blitter configuration when the blitter is ready
  when(stateReg === State.ready) {
    val config = Wire(new TilemapBlitterConfig)
    config.col := col
    config.row := row
    config.layer := io.layer
    config.tile := tileReg
    config.numColors := io.gameConfig.numColors
    config.rotate := io.options.rotate
    config.flip := io.options.flip
    blitter.io.config.enq(config)
  } otherwise {
    blitter.io.config.noenq()
  }

  // Enqueue valid tile ROM data into the tile FIFO
  when(io.tileRom.valid) {
    fifo.io.enq.enq(io.tileRom.dout)
  } otherwise {
    fifo.io.enq.noenq()
  }

  // Set last layer priority register
  when(stateReg === State.check && io.layerIndex === 0.U) {
    lastLayerPriorityReg := 0.U
  }.elsewhen(stateReg === State.done) {
    lastLayerPriorityReg := io.layer.priority
  }

  // FSM
  switch(stateReg) {
    // Wait for the start signal
    is(State.idle) {
      when(io.start) { stateReg := State.check }
    }

    // Check whether the layer is enabled
    is(State.check) {
      stateReg := Mux(disable, State.idle, State.load)
    }

    // Load the tile
    is(State.load) { stateReg := State.latch }

    // Latch the tile
    is(State.latch) { stateReg := State.ready }

    // Wait for the blitter to be ready
    is(State.ready) {
      when(blitter.io.config.ready) { stateReg := State.pending }
    }

    // Wait for the pixel data to be read for the next tile
    is(State.pending) {
      when(!burstReadyReg) { stateReg := State.next }
    }

    // Increment the col/row counter
    is(State.next) {
      stateReg := Mux(colWrap && rowWrap, State.done, State.load)
    }

    // Wait for the blitter to finish
    is(State.done) {
      when(!blitter.io.busy) { stateReg := State.idle }
    }
  }

  // Outputs
  io.busy := stateReg =/= State.idle
  io.layerRam.rd := stateReg === State.load
  io.layerRam.addr := layerRamAddr
  io.tileRom.rd := tileRomRead
  io.tileRom.addr := tileRomAddr
  io.tileRom.burstLength := tileRomBurstLength
  io.debug.idle := stateReg === State.idle
  io.debug.check := stateReg === State.check
  io.debug.load := stateReg === State.load
  io.debug.latch := stateReg === State.latch
  io.debug.ready := stateReg === State.ready
  io.debug.pending := stateReg === State.pending
  io.debug.next := stateReg === State.next
  io.debug.done := stateReg === State.done

  printf(p"TilemapProcessor(state: $stateReg, col: $col ($colWrap), row: $row ($rowWrap), ready: $burstReadyReg, pending: $burstPendingReg, full: ${ fifo.io.count })\n")
}
