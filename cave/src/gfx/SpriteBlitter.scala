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

package cave.gfx

import arcadia._
import arcadia.gfx.VideoIO
import arcadia.util.{Counter, PISO}
import cave._
import chisel3._
import chisel3.util._

/** Represents a sprite blitter configuration. */
class SpriteBlitterConfig extends Bundle {
  /** Sprite */
  val sprite = new Sprite
  /** Horizontal flip */
  val hFlip = Bool()
  /** Vertical flip */
  val vFlip = Bool()
}

/** The sprite blitter copies a sprite to the frame buffer. */
class SpriteBlitter extends Module {
  val io = IO(new Bundle {
    /** Video port */
    val video = Flipped(VideoIO())
    /** Config port */
    val config = DeqIO(new SpriteBlitterConfig)
    /** Enable flag */
    val enable = Input(Bool())
    /** Busy flag */
    val busy = Output(Bool())
    /** Pixel data port */
    val pixelData = DeqIO(Vec(Config.SPRITE_TILE_SIZE, Bits(Config.SPRITE_TILE_MAX_BPP.W)))
    /** Frame buffer port */
    val frameBuffer = new SpriteFrameBufferIO
  })

  // Registers
  val busyReg = RegInit(false.B)
  val configReg = RegEnable(io.config.bits, io.config.fire)

  // The PISO is used to buffer a single row of pixels to be copied to the frame buffer
  val piso = Module(new PISO(Config.SPRITE_TILE_SIZE, Bits(Config.SPRITE_TILE_MAX_BPP.W)))
  piso.io.rd := busyReg && !io.frameBuffer.waitReq
  piso.io.wr := io.pixelData.fire
  piso.io.din := io.pixelData.bits

  // Set PISO flags
  val pisoEmpty = piso.io.isEmpty
  val pisoAlmostEmpty = piso.io.isAlmostEmpty

  // Counters
  val (x, xWrap) = Counter.dynamic(configReg.sprite.size.x, enable = busyReg && !pisoEmpty && !io.frameBuffer.waitReq)
  val (y, yWrap) = Counter.dynamic(configReg.sprite.size.y, enable = xWrap)

  // Pixel position
  val posReg = RegEnable(SpriteBlitter.pixelPos(configReg.sprite, UVec2(x, y)), !io.frameBuffer.waitReq)

  // Decode the palette entry for the current pixel
  val pen = PaletteEntry(configReg.sprite.priority, configReg.sprite.colorCode, piso.io.dout)
  val penReg = RegEnable(pen, !io.frameBuffer.waitReq)
  val validReg = RegEnable(!pisoEmpty, !io.frameBuffer.waitReq)

  // The visible flag is asserted if the pixel is non-transparent and within the screen bounds
  val visible = validReg && GPU.isVisible(posReg) && !penReg.isTransparent

  // The done flag is asserted when the sprite has finished blitting
  val blitDone = xWrap && yWrap

  // The config ready flag is asserted when the blitter is ready to latch a new configuration (i.e.
  // the blitter is not busy, or a blit has just finished)
  val configReady = !busyReg || blitDone

  // The pixel data ready flag is asserted when the PISO is empty, or will be empty in the next
  // clock cycle
  val pixelDataReady = !io.frameBuffer.waitReq && (pisoEmpty || pisoAlmostEmpty)

  // Toggle busy register
  when(io.config.fire) { busyReg := true.B }.elsewhen(blitDone) { busyReg := false.B }

  // Outputs
  io.config.ready := configReady
  io.pixelData.ready := pixelDataReady
  io.frameBuffer.wr := io.enable && visible
  io.frameBuffer.addr := GPU.frameBufferAddr(posReg, configReg.hFlip, false.B)
  io.frameBuffer.mask := 3.U
  io.frameBuffer.din := penReg.asUInt
  io.busy := busyReg

  // Debug
  if (sys.env.get("DEBUG").contains("1")) {
    printf(p"SpriteBlitter(x: $x ($xWrap), y: $y ($yWrap), busy: $busyReg, configReady: $configReady, pixelDataReady: $pixelDataReady, write: ${ io.frameBuffer.wr }, pisoEmpty: ${ piso.io.isEmpty }, pisoAlmostEmpty: ${ piso.io.isAlmostEmpty })\n")
  }
}

object SpriteBlitter {
  /**
   * Calculates a sprite pixel position, applying the optional scale and flip transforms.
   *
   * @param sprite The sprite descriptor.
   * @param pos    The pixel position.
   */
  def pixelPos(sprite: Sprite, pos: UVec2): SVec2 = {
    // Convert sprite size to fixed-point value
    val size = sprite.size << Sprite.ZOOM_PRECISION

    // Scale x/y values
    val x = pos.x * sprite.zoom.x
    val y = pos.y * sprite.zoom.y

    // Flip x/y values
    val x_ = Mux(sprite.hFlip, size.x - x - (1 << Sprite.ZOOM_PRECISION).U, x)
    val y_ = Mux(sprite.vFlip, size.y - y - (1 << Sprite.ZOOM_PRECISION).U, y)

    // Adjusted sprite pixel position
    val result = sprite.pos + SVec2(x_, y_)

    // Truncate fractional bits
    result >> Sprite.ZOOM_PRECISION
  }
}
