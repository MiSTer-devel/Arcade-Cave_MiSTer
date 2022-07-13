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
    /** Game config port */
    val gameConfig = Input(GameConfig())
    /** Options port */
    val options = OptionsIO()
    /** Video port */
    val video = Flipped(VideoIO())
    /** Layer control ports */
    val layerCtrl = Vec(Config.LAYER_COUNT, LayerCtrlIO())
    /** Sprite control port */
    val spriteCtrl = SpriteCtrlIO()
    /** Sprite line buffer port */
    val spriteLineBuffer = new SpriteLineBufferIO
    /** Sprite frame buffer port */
    val spriteFrameBuffer = new SpriteFrameBufferIO
    /** System frame buffer port */
    val systemFrameBuffer = new SystemFrameBufferIO
    /** Palette RAM port */
    val paletteRam = new PaletteRamIO
    /** RGB output */
    val rgb = Output(RGB(Config.RGB_OUTPUT_BPP.W))
  })

  // Sprite processor
  val spriteProcessor = Module(new SpriteProcessor)
  spriteProcessor.io.video <> io.video
  spriteProcessor.io.ctrl <> io.spriteCtrl
  spriteProcessor.io.frameBuffer <> io.spriteFrameBuffer

  withClockAndReset(io.video.clock, io.video.reset) {
    // Layer processors
    val layerProcessor = 0.until(Config.LAYER_COUNT).map { i =>
      val p = Module(new LayerProcessor(i))
      p.io.video <> io.video
      p.io.ctrl <> io.layerCtrl(i)
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
    io.systemFrameBuffer.addr := RegNext(GPU.frameBufferAddr(io.video.size, io.video.pos, io.options.flip, io.options.rotate))
    io.systemFrameBuffer.mask := 0xf.U // 4 bytes
    io.systemFrameBuffer.din := RegNext(GPU.decodeABGR(colorMixer.io.dout))

    // Decode color mixer data and write it to the RGB output
    io.rgb := GPU.decodeRGB(colorMixer.io.dout)
  }
}

object GPU {
  /**
   * Transforms a pixel position to a frame buffer memory address, applying the flip and rotate
   * transforms.
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
   * Transforms a pixel position to a frame buffer memory address, applying the flip and rotate
   * transforms.
   *
   * @param pos    The pixel position.
   * @param flip   Flips the image.
   * @param rotate Rotates the image 90 degrees.
   * @return An address value.
   */
  def frameBufferAddr[T <: Bits with Num[T]](pos: Vec2[T], flip: Bool, rotate: Bool): UInt = {
    val x = pos.x(Config.FRAME_BUFFER_ADDR_WIDTH_X - 1, 0)
    val y = pos.y(Config.FRAME_BUFFER_ADDR_WIDTH_Y - 1, 0)
    val x_ = (Config.SCREEN_WIDTH - 1).U - x
    val y_ = (Config.SCREEN_HEIGHT - 1).U - y
    Mux(rotate,
      Mux(flip, (x * Config.SCREEN_HEIGHT.U) + y_, (x_ * Config.SCREEN_HEIGHT.U) + y),
      Mux(flip, (y_ * Config.SCREEN_WIDTH.U) + x_, (y * Config.SCREEN_WIDTH.U) + x)
    )
  }

  /**
   * Decodes Cave pixel data into a 24-bit RGB color value.
   *
   * @param data The color data.
   */
  private def decodeRGB(data: Bits): RGB = {
    val b = data(4, 0) ## data(4, 2)
    val r = data(9, 5) ## data(9, 7)
    val g = data(14, 10) ## data(14, 12)
    RGB(r, g, b)
  }

  /**
   * Decodes Cave pixel data into a 32-bit ABGR pixel.
   *
   * @param data The pixel data.
   * @return A 32-bit pixel value.
   */
  private def decodeABGR(data: Bits): Bits = {
    val rgb = decodeRGB(data)
    Cat(rgb.b, rgb.g, rgb.r).pad(32)
  }
}
