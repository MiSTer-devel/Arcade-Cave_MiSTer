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

import axon.Util
import axon.dma.ReadDMAIO
import axon.gfx._
import axon.mem._
import axon.types._
import cave._
import cave.types._
import chisel3._
import chisel3.util._

/** Graphics processing unit (GPU). */
class GPU extends Module {
  val io = IO(new Bundle {
    /** Video clock domain */
    val videoClock = Input(Clock())
    /** Video reset */
    val videoReset = Input(Bool())
    /** Game config port */
    val gameConfig = Input(GameConfig())
    /** Options port */
    val options = OptionsIO()
    /** Asserted when the program is ready for a new frame */
    val frameReady = Input(Bool())
    /** Layer ports */
    val layer = Vec(Config.LAYER_COUNT, LayerIO())
    /** Sprite port */
    val sprite = SpriteIO()
    /** Palette RAM port */
    val paletteRam = new PaletteRamIO
    /** Video port */
    val video = Flipped(VideoIO())
    /** RGB output */
    val rgb = Output(RGB(Config.RGB_OUTPUT_BPP.W))
    /** System frame buffer port */
    val systemFrameBuffer = new SystemFrameBufferIO
  })

  // Sprite processor
  //  val spriteProcessor = Module(new SpriteProcessor)
  //  spriteProcessor.io.start := io.frameReady
  //  spriteProcessor.io.sprite <> io.sprite
  //  spriteProcessor.io.frameBuffer <> frameBuffer.io.portA.asWriteMemIO
  io.sprite.vram.default()
  io.sprite.tileRom.default()

  // Layer 0 processor
  val layer0Processor = withClockAndReset(io.videoClock, io.videoReset) { Module(new TilemapProcessor) }
  layer0Processor.io.video <> io.video
  layer0Processor.io.layer <> io.layer(0)
  layer0Processor.io.offset := UVec2(0x6b.U, 0x11.U)

  // Layer 1 processor
  val layer1Processor = withClockAndReset(io.videoClock, io.videoReset) { Module(new TilemapProcessor) }
  layer1Processor.io.video <> io.video
  layer1Processor.io.layer <> io.layer(1)
  layer1Processor.io.offset := UVec2(0x6c.U, 0x11.U)

  // Layer 2 processor
  val layer2Processor = withClockAndReset(io.videoClock, io.videoReset) { Module(new TilemapProcessor) }
  layer2Processor.io.video <> io.video
  layer2Processor.io.layer <> io.layer(2)
  layer2Processor.io.offset := UVec2(Mux(io.layer(2).regs.tileSize, 0x6d.U, 0x75.U), 0x11.U)

  // Color mixer
  val colorMixer = withClockAndReset(io.videoClock, io.videoReset) { Module(new ColorMixer) }
  colorMixer.io.gameConfig <> io.gameConfig
  //  colorMixer.io.spritePen := frameBuffer.io.portB.dout.asTypeOf(new PaletteEntry)
  colorMixer.io.spritePen := PaletteEntry.zero
  colorMixer.io.layer0Pen := layer0Processor.io.pen
  colorMixer.io.layer1Pen := layer1Processor.io.pen
  colorMixer.io.layer2Pen := layer2Processor.io.pen
  colorMixer.io.paletteRam <> io.paletteRam

  // Write color mixer output to system frame buffer
  val addr = GPU.frameBufferAddr(io.video.pos, io.options.flip, io.options.rotate)
  val data = GPU.decodePixel(colorMixer.io.dout)
  io.systemFrameBuffer.wr := RegNext(io.video.displayEnable)
  io.systemFrameBuffer.burstCount := 1.U
  io.systemFrameBuffer.addr := RegNext(addr ## 0.U(2.W)) // FIXME
  io.systemFrameBuffer.mask := RegNext(Mux(addr(0), 0xf0.U, 0x0f.U)) // FIXME
  io.systemFrameBuffer.din := RegNext(data ## data) // FIXME

  // Decode analog RGB output from the color mixer data
  io.rgb := GPU.decodeRGB(colorMixer.io.dout)
}

object GPU {
  /**
   * Transforms a pixel position to a frame buffer memory address.
   *
   * @param pos The pixel position.
   * @return A memory address.
   */
  def frameBufferAddr(pos: UVec2): UInt = {
    val x = pos.x(Config.FRAME_BUFFER_ADDR_WIDTH_X - 1, 0)
    val y = pos.y(Config.FRAME_BUFFER_ADDR_WIDTH_Y - 1, 0)
    (y * Config.SCREEN_WIDTH.U) + x
  }

  /**
   * Transforms a pixel position to a frame buffer memory address, applying the optional flip
   * transform.
   *
   * @param pos  The pixel position.
   * @param flip Flips the horizontal axis when asserted.
   * @return A memory address.
   */
  def frameBufferAddr(pos: SVec2, flip: Bool): UInt = {
    val x = pos.x(Config.FRAME_BUFFER_ADDR_WIDTH_X - 1, 0)
    val y = pos.y(Config.FRAME_BUFFER_ADDR_WIDTH_Y - 1, 0)
    val x_ = (Config.SCREEN_WIDTH - 1).U - x
    val y_ = (Config.SCREEN_HEIGHT - 1).U - y
    Mux(flip, (y_ * Config.SCREEN_WIDTH.U) + x_, (y * Config.SCREEN_WIDTH.U) + x)
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
  def frameBufferAddr(pos: UVec2, flip: Bool, rotate: Bool): UInt = {
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
   * Creates a virtual write-only memory interface that writes a constant value to the given
   * address.
   *
   * @param addr The address value.
   * @param data The constant value.
   */
  def clearMem(addr: UInt, data: Bits = 0.U): WriteMemIO = {
    val mem = Wire(WriteMemIO(Config.OUTPUT_FRAME_BUFFER_ADDR_WIDTH, Config.OUTPUT_FRAME_BUFFER_DATA_WIDTH))
    mem.wr := true.B
    mem.addr := addr
    mem.mask := 0.U
    mem.din := data
    mem
  }

  /**
   * Decodes Cave pixel data into a 32-bit RGBA pixel.
   *
   * @param data The pixel data.
   * @return A 32-bit pixel value.
   */
  private def decodePixel(data: Bits): Bits = {
    val b = data(4, 0) ## data(4, 2)
    val r = data(9, 5) ## data(9, 7)
    val g = data(14, 10) ## data(14, 12)
    Cat(b, g, r).pad(32)
  }

  /**
   * Decodes Cave pixel data into a list of 32-bit RGBA pixels.
   *
   * @param data The pixel data.
   * @param n    The number of pixels.
   * @return A list of 32-bit pixel values.
   */
  private def decodePixels(data: Bits, n: Int): Seq[Bits] =
    Util
      // Decode channels
      .decode(data, n * 3, 5)
      // Convert channel values to 8BPP
      .map { c => c(4, 0) ## c(4, 2) }
      // Group channels
      .grouped(3).toSeq
      // Reorder channels (BRG -> ABGR)
      .map { case Seq(b, r, g) => Cat(0.U(8.W), b, g, r) }
      // Swap pixels values
      .reverse

  /**
   * Decodes Cave pixel data into a 24-bit RGB color value.
   *
   * @param data The color data.
   */
  private def decodeRGB(data: UInt): RGB = {
    val b = data(4, 0) ## data(4, 2)
    val r = data(9, 5) ## data(9, 7)
    val g = data(14, 10) ## data(14, 12)
    RGB(r, g, b)
  }

  /**
   * Calculates the visibility of a pixel.
   *
   * @param pos The pixel position.
   * @return A boolean value indicating whether the pixel is visible.
   */
  def isVisible(pos: SVec2): Bool =
    Util.between(pos.x, 0 until Config.SCREEN_WIDTH) &&
      Util.between(pos.y, 0 until Config.SCREEN_HEIGHT)
}
