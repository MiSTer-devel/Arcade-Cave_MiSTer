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
import arcadia.gfx._
import arcadia.mister.OptionsIO
import cave._
import chisel3._
import chisel3.util._

/**
 * The graphics processing unit (GPU) handles rendering the sprite and tilemap layers.
 *
 * For each pixel on the screen, the palette entry outputs from all layers are mixed together to
 * yield a final palette entry. This value is then used to look up the actual pixel color in the
 * palette RAM.
 */
class GPU extends Module {
  val io = IO(new Bundle {
    /** Video clock domain */
    val videoClock = Input(Clock())
    /** Layer control ports */
    val layerCtrl = Vec(Config.LAYER_COUNT, LayerCtrlIO())
    /** Sprite control port */
    val spriteCtrl = SpriteCtrlIO()
    /** Game config port */
    val gameConfig = Input(GameConfig())
    /** Options port */
    val options = Input(OptionsIO())
    /** Video port */
    val video = Input(VideoIO())
    /** Sprite line buffer port */
    val spriteLineBuffer = new SpriteLineBufferIO
    /** Sprite frame buffer port */
    val spriteFrameBuffer = new SpriteFrameBufferIO
    /** System frame buffer port */
    val systemFrameBuffer = new SystemFrameBufferIO
    /** Palette RAM port */
    val paletteRam = new PaletteRamIO
    /** RGB output */
    val rgb = Output(UInt(Config.RGB_WIDTH.W))
  })

  // Sprite processor
  val spriteProcessor = Module(new SpriteProcessor)
  spriteProcessor.io.ctrl <> io.spriteCtrl
  spriteProcessor.io.video := io.video
  spriteProcessor.io.frameBuffer <> io.spriteFrameBuffer

  withClock(io.videoClock) {
    // Layer processors
    val layerProcessor = 0.until(Config.LAYER_COUNT).map { i =>
      val p = Module(new LayerProcessor(i))
      p.io.ctrl <> io.layerCtrl(i)
      p.io.video := io.video
      p.io.spriteOffset := io.spriteCtrl.regs.offset
      p
    }

    // Color mixer
    val colorMixer = Module(new ColorMixer)
    colorMixer.io.gameConfig <> io.gameConfig
    colorMixer.io.spritePen := io.spriteLineBuffer.dout.asTypeOf(new PaletteEntry)
    colorMixer.io.layer0Pen := layerProcessor(0).io.pen
    colorMixer.io.layer1Pen := layerProcessor(1).io.pen
    colorMixer.io.layer2Pen := layerProcessor(2).io.pen
    colorMixer.io.paletteRam <> io.paletteRam

    // Read next pixel from the sprite line buffer
    io.spriteLineBuffer.rd := true.B // read-only
    io.spriteLineBuffer.addr := io.video.pos.x

    // Decode color mixer data and write it to the system frame buffer
    io.systemFrameBuffer.wr := RegNext(io.video.clockEnable && io.video.displayEnable)
    io.systemFrameBuffer.addr := RegNext(GPU.frameBufferAddr(io.video.size, io.video.pos, io.options.flipVideo, io.options.rotate))
    io.systemFrameBuffer.mask := 0xf.U // 4 bytes
    io.systemFrameBuffer.din := RegNext(GPU.decodeABGR(colorMixer.io.dout))

    // Decode color mixer data and write it to the RGB output
    io.rgb := GPU.decodeRGB(colorMixer.io.dout)
  }
}

object GPU {
  /**
   * Transforms a position vector to a frame buffer memory address, applying the optional flip
   * and rotate transforms.
   *
   * @param size   The frame buffer size.
   * @param pos    The pixel position.
   * @param flip   Flips the image.
   * @param rotate Rotates the image 90 degrees.
   * @return An address value.
   */
  def frameBufferAddr(size: UVec2, pos: UVec2, flip: Bool, rotate: Bool): UInt = {
    val x = pos.x
    val y = pos.y
    val x_ = size.x - pos.x - 1.U
    val y_ = size.y - pos.y - 1.U
    Mux(rotate,
      Mux(flip, (x * size.y) + y_, (x_ * size.y) + y),
      Mux(flip, (y_ * size.x) + x_, (y * size.x) + x)
    )
  }

  /**
   * Decodes a 24 bit RGB value from the given pixel data.
   *
   * @param data The pixel data.
   * @return A 24 bit RGB value.
   */
  private def decodeRGB(data: Bits): UInt = {
    val b = data(4, 0) ## data(4, 2)
    val r = data(9, 5) ## data(9, 7)
    val g = data(14, 10) ## data(14, 12)
    r ## g ## b
  }

  /**
   * Decodes a 32 bit ABGR value from the given pixel data.
   *
   * @param data The pixel data.
   * @return A 32 bit ARGB value.
   */
  def decodeABGR(data: Bits): Bits = {
    val b = data(4, 0) ## data(4, 2)
    val r = data(9, 5) ## data(9, 7)
    val g = data(14, 10) ## data(14, 12)
    Cat(b, g, r).pad(32)
  }
}
