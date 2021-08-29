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

/** Represents a tile blitter configuration. */
class TilemapBlitterConfig extends Bundle {
  /** Tilemap column */
  val col = UInt(Config.TILEMAP_MAX_COLS.W)
  /** Tilemap row */
  val row = UInt(Config.TILEMAP_MAX_ROWS.W)
  /** Layer */
  val layer = new Layer
  /** Tile */
  val tile = new Tile
  /** Number of colors per palette */
  val numColors = UInt(9.W)
  /** Asserted when screen rotation is enabled */
  val rotate = Bool()
  /** Asserted when screen flipping is enabled */
  val flip = Bool()
}

/** The tilemap blitter copies a tile to the frame buffer. */
class TilemapBlitter extends Module {
  val io = IO(new Bundle {
    /** Config port */
    val config = DeqIO(new TilemapBlitterConfig)
    /** Layer index */
    val layerIndex = Input(UInt(2.W))
    /** Previous layer priority value */
    val lastLayerPriority = Input(UInt(Config.PRIO_WIDTH.W))
    /** Pixel data port */
    val pixelData = DeqIO(Vec(Config.SMALL_TILE_SIZE, Bits(Config.TILE_MAX_BPP.W)))
    /** Palette RAM port */
    val paletteRam = ReadMemIO(Config.PALETTE_RAM_GPU_ADDR_WIDTH, Config.PALETTE_RAM_GPU_DATA_WIDTH)
    /** Priority port */
    val priority = new PriorityIO
    /** Frame buffer port */
    val frameBuffer = WriteMemIO(Config.FRAME_BUFFER_ADDR_WIDTH, Config.FRAME_BUFFER_DATA_WIDTH)
    /** Busy flag */
    val busy = Output(Bool())
  })

  // Registers
  val busyReg = RegInit(false.B)
  val configReg = RegEnable(io.config.bits, io.config.fire())
  val paletteEntryReg = Reg(new PaletteEntry)

  // The PISO buffers the pixels to be copied to the frame buffer
  val piso = Module(new PISO(Config.SMALL_TILE_SIZE, Bits(Config.TILE_MAX_BPP.W)))
  piso.io.wr := io.pixelData.fire()
  piso.io.din := io.pixelData.bits

  // Set PISO flags
  val pisoEmpty = piso.io.isEmpty
  val pisoAlmostEmpty = piso.io.isAlmostEmpty

  // Counters
  val (x, xWrap) = Counter.static(Config.SMALL_TILE_SIZE, enable = busyReg && !pisoEmpty)
  val (y, yWrap) = Counter.static(Config.SMALL_TILE_SIZE, enable = xWrap)
  val (subTileX, subTileXWrap) = Counter.static(2, enable = configReg.layer.tileSize && xWrap && yWrap)
  val (subTileY, subTileYWrap) = Counter.static(2, enable = subTileXWrap)

  // Set done flag
//  val smallTileDone = xWrap && yWrap
//  val largeTileDone = xWrap && yWrap && subTileXWrap && subTileYWrap
//  val blitDone = Mux(configReg.layer.tileSize, largeTileDone, smallTileDone)
  val blitDone = xWrap && yWrap && (!configReg.layer.tileSize || (subTileXWrap && subTileYWrap))

  // Pixel position
  val pixelPos = UVec2(x, y)

  // Tile position
  val tilePos = {
    val x = Mux(configReg.layer.tileSize, configReg.col ## subTileX ## 0.U(3.W), configReg.col ## 0.U(3.W))
    val y = Mux(configReg.layer.tileSize, configReg.row ## subTileY ## 0.U(3.W), configReg.row ## 0.U(3.W))
    UVec2(x, y)
  }

  // Tile offset
  val scrollPos = {
    val offset = configReg.layer.scroll + configReg.layer.magicOffset(io.layerIndex)
    val x = Mux(configReg.layer.tileSize, offset.x(3, 0), offset.x(2, 0))
    val y = Mux(configReg.layer.tileSize, offset.y(3, 0), offset.y(2, 0))
    UVec2(x, y)
  }

  // Pixel position pipeline
  val stage0Pos = pixelPos + tilePos - scrollPos
  val stage1Pos = RegNext(stage0Pos)
  val stage2Pos = RegNext(stage1Pos)

  // The busy register is set when a configuration is latched, and cleared when a blit has finished
  when(io.config.fire()) { busyReg := true.B }.elsewhen(blitDone) { busyReg := false.B }

  // The FIFO can only be read when it is not empty and should be read if the PISO is empty or will
  // be empty next clock cycle. Since the pipeline after the FIFO has no backpressure, and can
  // accommodate data every clock cycle, this will be the case if the PISO counter is one.
  val pixelDataReady = io.pixelData.valid && busyReg && (pisoEmpty || pisoAlmostEmpty)

  // The config ready flag is asserted when the blitter is ready to latch a new configuration (i.e.
  // the blitter is not busy, or a blit has just finished)
  val configReady = io.config.valid && (!busyReg || blitDone)

  // The tiles use the second 64 palettes, and use 16 colors (out of 256 possible in a palette)
  paletteEntryReg := PaletteEntry(1.U ## configReg.tile.colorCode, piso.io.dout)
  val paletteRamAddr = paletteEntryReg.toAddr(configReg.numColors)

  // Set delayed valid/done shift registers
  val validReg = ShiftRegister(!pisoEmpty, 2, false.B, true.B)
  val delayedBusyReg = ShiftRegister(busyReg, 2, false.B, true.B)

  // Set priority data
  val priorityReadAddr = GPU.transformAddr(stage1Pos, configReg.flip, configReg.rotate)
  val priorityReadData = io.priority.read.dout
  val priorityWriteData = ShiftRegister(configReg.tile.priority, 3)

  // The current pixel has priority if it has more priority than the previous pixel. Otherwise, if
  // the pixel priorities are the same then it depends on the layer priorities.
  val hasPriority = (configReg.tile.priority > priorityReadData) ||
                    (configReg.tile.priority === priorityReadData && configReg.layer.priority >= io.lastLayerPriority)

  // The transparency flag must be delayed by one cycle, since the colors come from the palette RAM
  // they arrive one cycle later.
  val visible = hasPriority && GPU.isVisible(stage2Pos) && !RegNext(paletteEntryReg.isTransparent)

  // Set frame buffer signals
  val frameBufferWrite = RegNext(validReg && visible)
  val frameBufferAddr = RegNext(GPU.transformAddr(stage2Pos, configReg.flip, configReg.rotate))
  val frameBufferData = RegNext(io.paletteRam.dout)

  // Outputs
  io.config.ready := configReady
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
  io.busy := delayedBusyReg
}
