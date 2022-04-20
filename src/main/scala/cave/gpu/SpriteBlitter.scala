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
import axon.util.{Counter, PISO}
import cave.Config
import cave.types._
import chisel3._
import chisel3.util._

/** Represents a sprite blitter configuration. */
class SpriteBlitterConfig extends Bundle {
  /** Sprite */
  val sprite = new Sprite
  /** Asserted when screen rotation is enabled */
  val rotate = Bool()
  /** Asserted when screen flipping is enabled */
  val flip = Bool()
}

/** The sprite blitter copies a sprite to the frame buffer. */
class SpriteBlitter extends Module {
  val io = IO(new Bundle {
    /** Config port */
    val config = DeqIO(new SpriteBlitterConfig)
    /** Busy flag */
    val busy = Output(Bool())
    /** Pixel data port */
    val pixelData = DeqIO(Vec(Config.SPRITE_TILE_SIZE, Bits(Config.TILE_MAX_BPP.W)))
    /** Frame buffer port */
    val frameBuffer = WriteMemIO(Config.FRAME_BUFFER_ADDR_WIDTH, Config.FRAME_BUFFER_DATA_WIDTH)
  })

  // Registers
  val busyReg = RegInit(false.B)
  val configReg = RegEnable(io.config.bits, io.config.fire)

  // The PISO is used to buffer a single row of pixels to be copied to the frame buffer
  val piso = Module(new PISO(Config.SPRITE_TILE_SIZE, Bits(Config.TILE_MAX_BPP.W)))
  piso.io.wr := io.pixelData.fire
  piso.io.din := io.pixelData.bits

  // Set PISO flags
  val pisoEmpty = piso.io.isEmpty
  val pisoAlmostEmpty = piso.io.isAlmostEmpty

  // Counters
  val (x, xWrap) = Counter.dynamic(configReg.sprite.size.x, enable = busyReg && !pisoEmpty)
  val (y, yWrap) = Counter.dynamic(configReg.sprite.size.y, enable = xWrap)

  // Pixel position pipeline
  val stage0Pos = configReg.sprite.pixelPos(x, y)
  val stage1Pos = RegNext(stage0Pos)
  val stage2Pos = RegNext(stage1Pos)

  // The done flag is asserted when the sprite has finished blitting
  val blitDone = xWrap && yWrap

  // The busy register is set when a configuration is latched, and cleared when a blit has finished
  when(io.config.fire) { busyReg := true.B }.elsewhen(blitDone) { busyReg := false.B }

  // The pixel data ready flag is asserted when the PISO is empty, or will be empty in the next
  // clock cycle
  val pixelDataReady = io.pixelData.valid && (pisoEmpty || pisoAlmostEmpty)

  // The config ready flag is asserted when the blitter is ready to latch a new configuration (i.e.
  // the blitter is not busy, or a blit has just finished)
  val configReady = io.config.valid && (!busyReg || blitDone)

  // Decode the palette entry for the current pixel
  val paletteEntryReg = RegNext(PaletteEntry(configReg.sprite.priority, configReg.sprite.colorCode, piso.io.dout))

  // Set delayed shift registers
  val validReg = ShiftRegister(!pisoEmpty, 2, false.B, true.B)
  val delayedBusyReg = ShiftRegister(busyReg, 2, false.B, true.B)

  // Set visible flag
  val visible = GPU.isVisible(stage1Pos) && !paletteEntryReg.isTransparent

  // Set frame buffer signals
  val frameBufferWrite = RegNext(validReg && visible)
  val frameBufferAddr = RegNext(GPU.transformAddr(stage1Pos, configReg.flip, configReg.rotate))
  val frameBufferData = RegNext(paletteEntryReg.asUInt)

  // Outputs
  io.config.ready := configReady
  io.pixelData.ready := pixelDataReady
  io.frameBuffer.wr := frameBufferWrite
  io.frameBuffer.addr := frameBufferAddr
  io.frameBuffer.mask := 0.U
  io.frameBuffer.din := frameBufferData
  io.busy := delayedBusyReg

  printf(p"SpriteBlitter(x: $x ($xWrap), y: $y ($yWrap), ready: $configReady, busy: $busyReg, write: $frameBufferWrite, pixelDataReady: $pixelDataReady, pisoEmpty: ${ piso.io.isEmpty }, pisoAlmostEmpty: ${ piso.io.isAlmostEmpty })\n")
}
