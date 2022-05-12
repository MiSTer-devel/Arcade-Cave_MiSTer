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

package cave.fb

import axon.Util
import axon.gfx.VideoIO
import axon.mem._
import axon.mem.dma.{BurstWriteDMA, BurstReadDMA}
import cave.Config
import cave.types._
import chisel3._
import chisel3.util._

/**
 * The sprite frame buffer used for buffering pixel data rendered by the sprite layer.
 *
 * On the original CAVE arcade hardware, there are two V53C16256H DRAM chips that provide dedicated
 * memory for the sprite frame buffer. There are two of them because double buffering is used.
 *
 * Unfortunately, there are not enough resources available in the FGPA for us to replicate the
 * original design directly in BRAM, so we need to be a bit more creative.
 *
 * The strategy used here is to implement a single 320x240x16BPP frame buffer in BRAM, which the GPU
 * use for rendering sprites. When a frame is completed, the contents of the frame buffer is copied
 * to DDR memory using a DMA controller.
 *
 * Meanwhile, the scanline for the current vertical position is copied from DDR memory into a line
 * buffer in BRAM using another DMA controller. The GPU reads the contents of the line buffer to get
 * the pixel at the current horizontal position.
 */
class SpriteFrameBuffer extends Module {
  val io = IO(new Bundle {
    /** Enables the sprite frame buffer */
    val enable = Input(Bool())
    /** Asserted when the sprite processor is busy */
    val spriteProcessorBusy = Input(Bool())
    /** Video port */
    val video = Flipped(VideoIO())
    /** GPU-side */
    val gpu = new Bundle {
      /** Line buffer port */
      val lineBuffer = Flipped(new SpriteLineBufferIO)
      /** Frame buffer port */
      val frameBuffer = Flipped(new SpriteFrameBufferIO)
    }
    /** DDR-side */
    val ddr = new Bundle {
      /** Line buffer port */
      val lineBuffer = BurstReadMemIO(Config.ddrConfig)
      /** Frame buffer port */
      val frameBuffer = BurstWriteMemIO(Config.ddrConfig)
    }
  })

  // Set the blanking signal strobes
  val hBlankStart = Util.rising(ShiftRegister(io.video.hBlank, 2))
  val vBlankStart = Util.rising(ShiftRegister(io.video.vBlank, 2))

  // Set the sprite processor done strobe
  val spriteProcessorDone = Util.falling(io.spriteProcessorBusy)

  // Line buffer (320x1x16BPP)
  val lineBuffer = Module(new TrueDualPortRam(
    addrWidthA = Config.FRAME_BUFFER_ADDR_WIDTH_X - 2,
    dataWidthA = Config.SPRITE_FRAME_BUFFER_DATA_WIDTH * 4,
    depthA = Some(Config.SCREEN_WIDTH / 4),
    addrWidthB = Config.FRAME_BUFFER_ADDR_WIDTH_X,
    dataWidthB = Config.SPRITE_FRAME_BUFFER_DATA_WIDTH,
    depthB = Some(Config.SCREEN_WIDTH)
  ))
  lineBuffer.io.clockB := io.video.clock
  lineBuffer.io.portB <> io.gpu.lineBuffer

  // Frame buffer (320x240x16BPP)
  val frameBuffer = Module(new DualPortRam(
    addrWidthA = Config.FRAME_BUFFER_ADDR_WIDTH,
    dataWidthA = Config.SPRITE_FRAME_BUFFER_DATA_WIDTH,
    depthA = Some(Config.FRAME_BUFFER_DEPTH),
    addrWidthB = Config.FRAME_BUFFER_ADDR_WIDTH - 2,
    dataWidthB = Config.SPRITE_FRAME_BUFFER_DATA_WIDTH * 4,
    depthB = Some(Config.FRAME_BUFFER_DEPTH / 4)
  ))
  frameBuffer.io.portA <> io.gpu.frameBuffer

  // The page flipper uses double buffering for sprites. The pages are swapped at the start of every
  // vertical blank.
  val pageFlipper = Module(new PageFlipper(Config.SPRITE_FRAME_BUFFER_BASE_ADDR))
  pageFlipper.io.mode := false.B
  pageFlipper.io.swapRead := false.B
  pageFlipper.io.swapWrite := vBlankStart

  // Copy line buffer from DDR memory to BRAM
  val lineBufferDma = Module(new BurstReadDMA(Config.spriteLineBufferDmaConfig))
  lineBufferDma.io.start := io.enable && hBlankStart
  lineBufferDma.io.in.mapAddr(
    _ + pageFlipper.io.addrRead + ((io.video.pos.y + 1.U) * (Config.SCREEN_WIDTH * 2).U) // FIXME: avoid expensive multiply
  ) <> io.ddr.lineBuffer
  lineBufferDma.io.out.mapAddr { a =>
    (a >> 3).asUInt // convert from byte address
  }.asReadWriteMemIO <> lineBuffer.io.portA

  // Copy frame buffer from BRAM to DDR memory
  val frameBufferDma = Module(new BurstWriteDMA(Config.spriteFrameBufferDmaConfig))
  frameBufferDma.io.start := io.enable && spriteProcessorDone
  frameBufferDma.io.in.mapAddr { a =>
    (a >> 3).asUInt // convert from byte address
  }.asReadMemIO <> frameBuffer.io.portB
  frameBufferDma.io.out.mapAddr(_ + pageFlipper.io.addrWrite) <> io.ddr.frameBuffer
}
