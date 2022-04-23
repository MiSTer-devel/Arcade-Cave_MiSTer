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

import axon.Util
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
    /** Video port */
    val video = VideoIO()
    /** Frame buffer DMA port */
    val frameBufferDMA = Flipped(new FrameBufferDMAIO)
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
    /** RGB output */
    val rgb = Output(RGB(Config.DDR_FRAME_BUFFER_BITS_PER_CHANNEL.W))
  })

  // Video timing
  val video = withClockAndReset(io.videoClock, io.videoReset) {
    val videoTiming = Module(new VideoTiming(Config.originalVideoTimingConfig))
    videoTiming.io.offset := RegEnable(io.options.offset, videoTiming.io.video.vSync)
    videoTiming.io.video <> io.video
    videoTiming.io.video
  }

  // Sprite processor
//  val spriteProcessor = Module(new SpriteProcessor)
//  spriteProcessor.io.start := io.frameReady
//  spriteProcessor.io.sprite <> io.sprite
//  spriteProcessor.io.frameBuffer <> frameBuffer.io.portA.asWriteMemIO
  io.sprite.vram.default()
  io.sprite.tileRom.default()

  // Layer 0 processor
  val layer0Processor = withClockAndReset(io.videoClock, io.videoReset) { Module(new TilemapProcessor) }
  layer0Processor.io.video <> video
  layer0Processor.io.layer <> io.layer(0)
  layer0Processor.io.offset := UVec2(0x6b.U, 0x11.U)

  // Layer 1 processor
  val layer1Processor = withClockAndReset(io.videoClock, io.videoReset) { Module(new TilemapProcessor) }
  layer1Processor.io.video <> video
  layer1Processor.io.layer <> io.layer(1)
  layer1Processor.io.offset := UVec2(0x6c.U, 0x11.U)

  // Layer 2 processor
  val layer2Processor = withClockAndReset(io.videoClock, io.videoReset) { Module(new TilemapProcessor) }
  layer2Processor.io.video <> video
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

  // Frame buffer
  val frameBuffer = withClockAndReset(io.videoClock, io.videoReset) {
    val mem = Module(new TrueDualPortRam(
      addrWidthA = Config.FRAME_BUFFER_ADDR_WIDTH,
      dataWidthA = Config.FRAME_BUFFER_DATA_WIDTH,
      depthA = Some(Config.FRAME_BUFFER_DEPTH),
      addrWidthB = Config.FRAME_BUFFER_DMA_ADDR_WIDTH,
      dataWidthB = Config.FRAME_BUFFER_DATA_WIDTH * Config.FRAME_BUFFER_DMA_PIXELS,
      depthB = Some(Config.FRAME_BUFFER_DMA_DEPTH)
    ))
    mem.io.portA.rd := false.B
    mem.io.portA.wr := RegNext(video.displayEnable)
    mem.io.portA.addr := RegNext(GPU.frameBufferAddr(video.pos, io.options.flip, io.options.rotate))
    mem.io.portA.mask := 0.U
    mem.io.portA.din := RegNext(colorMixer.io.dout)
    mem
  }
  frameBuffer.io.clockB := clock // frame buffer DMA runs in the system clock domain

  // Decode the pixel data to 24-bit pixels
  frameBuffer.io.portB <> io.frameBufferDMA.mapData(data =>
    GPU.decodePixels(data, Config.FRAME_BUFFER_DMA_PIXELS).reduce(_ ## _).asUInt
  )

  // Decode RGB output from palette data
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
    val mem = Wire(WriteMemIO(Config.FRAME_BUFFER_ADDR_WIDTH, Config.FRAME_BUFFER_DATA_WIDTH))
    mem.wr := true.B
    mem.addr := addr
    mem.mask := 0.U
    mem.din := data
    mem
  }

  /**
   * Decodes a list of pixels.
   *
   * @param data The pixel data.
   * @param n    The number of pixels.
   * @return A list of 24-bit pixel values.
   */
  private def decodePixels(data: Bits, n: Int): Seq[Bits] =
    Util
      // Decode channels
      .decode(data, n * 3, Config.BITS_PER_CHANNEL)
      // Convert channel values to 8BPP
      .map { c => c(4, 0) ## c(4, 2) }
      // Group channels
      .grouped(3).toSeq
      // Reorder channels (BRG -> BGR)
      .map { case Seq(b, r, g) => Cat(b, g, r) }
      // Swap pixels values
      .reverse

  /**
   * Decodes a RGB color from a 16-bit word.
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
