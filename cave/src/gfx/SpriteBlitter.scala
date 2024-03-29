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
    /** Enable flag */
    val enable = Input(Bool())
    /** Busy flag */
    val busy = Output(Bool())
    /** Config port */
    val config = DeqIO(new SpriteBlitterConfig)
    /** Video port */
    val video = Input(VideoIO())
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
  piso.io.rd := busyReg && io.frameBuffer.wait_n
  piso.io.wr := io.pixelData.fire
  piso.io.din := io.pixelData.bits

  // Set PISO flags
  val pisoEmpty = piso.io.isEmpty
  val pisoAlmostEmpty = piso.io.isAlmostEmpty

  // Counters
  val (x, xWrap) = Counter.dynamic(configReg.sprite.size.x, enable = busyReg && !pisoEmpty && io.frameBuffer.wait_n)
  val (y, yWrap) = Counter.dynamic(configReg.sprite.size.y, enable = xWrap)

  // Pixel position
  val posReg = RegEnable(SpriteBlitter.pixelPos(configReg.sprite, UVec2(x, y)), io.frameBuffer.wait_n)

  // Decode the palette entry for the current pixel
  val pen = PaletteEntry(configReg.sprite.priority, configReg.sprite.colorCode, piso.io.dout)
  val penReg = RegEnable(pen, io.frameBuffer.wait_n)
  val validReg = RegEnable(!pisoEmpty, io.frameBuffer.wait_n)

  // The visible flag is asserted if the pixel is non-transparent and within the screen bounds
  val visible = SpriteBlitter.isVisible(io.video.size, posReg) && validReg && !penReg.isTransparent

  // The done flag is asserted when the sprite has finished blitting
  val blitDone = xWrap && yWrap

  // The config ready flag is asserted when the blitter is ready to latch a new configuration (i.e.
  // the blitter is not busy, or a blit has just finished)
  val configReady = !busyReg || blitDone

  // The pixel data ready flag is asserted when the PISO is empty, or will be empty in the next
  // clock cycle
  val pixelDataReady = io.frameBuffer.wait_n && (pisoEmpty || pisoAlmostEmpty)

  // Toggle busy register
  when(io.config.fire) { busyReg := true.B }.elsewhen(blitDone) { busyReg := false.B }

  // Outputs
  io.config.ready := configReady
  io.pixelData.ready := pixelDataReady
  io.frameBuffer.wr := io.enable && visible
  io.frameBuffer.addr := SpriteBlitter.frameBufferAddr(io.video.size, posReg, configReg.hFlip)
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
   * @return An unsigned vector.
   */
  def pixelPos(sprite: Sprite, pos: UVec2): UVec2 = {
    // Convert sprite size to fixed-point value
    val size = sprite.size << Sprite.ZOOM_PRECISION

    // Scale x/y values
    val x = pos.x * sprite.zoom.x
    val y = pos.y * sprite.zoom.y

    // Flip x/y values
    val x_ = Mux(sprite.hFlip, size.x - x - (1 << Sprite.ZOOM_PRECISION).U, x)
    val y_ = Mux(sprite.vFlip, size.y - y - (1 << Sprite.ZOOM_PRECISION).U, y)

    // Adjusted sprite pixel position
    val adjusted = sprite.pos + SVec2(x_, y_)

    // Truncate fractional bits
    val result = adjusted >> Sprite.ZOOM_PRECISION

    UVec2(result.x, result.y)
  }

  /**
   * Transforms a pixel position to a frame buffer memory address, applying the optional flip
   * transform.
   *
   * @param size The frame buffer size.
   * @param pos  The pixel position.
   * @param flip Flips the image.
   * @return An address value.
   */
  def frameBufferAddr(size: UVec2, pos: UVec2, flip: Bool): UInt = {
    val x = pos.x
    val y = pos.y
    val x_ = size.x - pos.x - 1.U
    val y_ = size.y - pos.y - 1.U
    Mux(flip,
      (y_ << log2Ceil(Config.FRAME_BUFFER_WIDTH)).asUInt + x_,
      (y << log2Ceil(Config.FRAME_BUFFER_WIDTH)).asUInt + x
    )
  }

  /**
   * Calculates the visibility of a pixel.
   *
   * @param size The frame buffer size.
   * @param pos  The pixel position.
   * @return A boolean value indicating whether the pixel is visible.
   */
  def isVisible(size: UVec2, pos: UVec2): Bool =
    Util.between(pos.x, 0.U, size.x - 1.U) && Util.between(pos.y, 0.U, size.y - 1.U)
}
