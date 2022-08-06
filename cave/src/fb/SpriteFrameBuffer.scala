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

import arcadia.Util
import arcadia.gfx.VideoIO
import arcadia.mem._
import arcadia.mem.arbiter.BurstMemArbiter
import arcadia.mem.dma.{BurstReadDMA, BurstWriteDMA}
import cave._
import chisel3._
import chisel3.util._

/**
 * The sprite frame buffer used for buffering pixel data rendered by the sprite layer.
 *
 * The original CAVE arcade hardware has two V53C16256H DRAM chips that are used for the sprite
 * frame buffer (two are required because sprites use double buffering).
 *
 * Unfortunately, there are not enough resources available in the FPGA for us to replicate the
 * original design directly in BRAM, so we need to be a bit more creative.
 *
 * The strategy used here is to place the sprite frame buffer in DDR memory. At the beginning of
 * each frame, the frame buffer pages are swapped and the current page is cleared using a DMA
 * transfer.
 *
 * Meanwhile, the current scanline is copied from DDR memory into the line buffer using a DMA
 * transfer. The GPU then reads the contents of the line buffer to get the sprite pixel at the
 * current horizontal position.
 */
class SpriteFrameBuffer extends Module {
  val io = IO(new Bundle {
    /** Video clock domain */
    val videoClock = Input(Clock())
    /** Enable the frame buffer */
    val enable = Input(Bool())
    /** Swap the current page */
    val swap = Input(Bool())
    /** Video port */
    val video = Flipped(VideoIO())
    /** Line buffer port */
    val lineBuffer = Flipped(new SpriteLineBufferIO)
    /** Frame buffer port */
    val frameBuffer = Flipped(new SpriteFrameBufferIO)
    /** DDR port */
    val ddr = BurstMemIO(Config.ddrConfig)
  })

  // Line buffer
  val lineBuffer = Module(new TrueDualPortRam(
    addrWidthA = Config.FRAME_BUFFER_ADDR_WIDTH_X - 2,
    dataWidthA = Config.SPRITE_FRAME_BUFFER_DATA_WIDTH * 4,
    depthA = Some(Config.FRAME_BUFFER_WIDTH / 4),
    addrWidthB = Config.FRAME_BUFFER_ADDR_WIDTH_X,
    dataWidthB = Config.SPRITE_FRAME_BUFFER_DATA_WIDTH,
    depthB = Some(Config.FRAME_BUFFER_WIDTH)
  ))
  lineBuffer.io.clockB := io.videoClock
  lineBuffer.io.portB <> io.lineBuffer

  // The page flipper uses double buffering for sprites. The pages are swapped at the start of every
  // vertical blank.
  val pageFlipper = Module(new PageFlipper(Config.SPRITE_FRAME_BUFFER_BASE_ADDR))
  pageFlipper.io.mode := false.B
  pageFlipper.io.swapRead := false.B
  pageFlipper.io.swapWrite := io.enable && io.swap

  // Start line buffer DMA as soon as the current line has finished
  val lineBufferDmaStart = Util.rising(ShiftRegister(io.video.hBlank, 2))

  // Copy line buffer from DDR memory to BRAM
  val lineBufferDma = Module(new BurstReadDMA(Config.spriteLineBufferDmaConfig))
  lineBufferDma.io.start := io.enable && lineBufferDmaStart
  lineBufferDma.io.out
    .mapAddr { a => (a >> 3).asUInt } // convert from byte address
    .asMemIO <> lineBuffer.io.portA

  // Queue frame buffer requests
  val queue = Module(new RequestQueue(
    inAddrWidth = Config.FRAME_BUFFER_ADDR_WIDTH,
    inDataWidth = Config.SPRITE_FRAME_BUFFER_DATA_WIDTH,
    outAddrWidth = Config.ddrConfig.addrWidth,
    outDataWidth = Config.ddrConfig.dataWidth,
    depth = Config.SPRITE_FRAME_BUFFER_REQUEST_QUEUE_DEPTH
  ))
  queue.io.enable := io.enable
  queue.io.readClock := clock

  // Clear frame buffer when the swap signal is asserted
  val frameBufferDma = Module(new BurstWriteDMA(Config.spriteFrameBufferDmaConfig))
  frameBufferDma.io.start := io.enable && io.swap
  frameBufferDma.io.in.valid := true.B
  frameBufferDma.io.in.waitReq := false.B
  frameBufferDma.io.in.dout := 0.U

  // Calculate line buffer address offset for the next scanline
  val lineBufferAddrOffset = ((io.video.pos.y + 1.U) << log2Ceil(Config.SPRITE_FRAME_BUFFER_STRIDE)).asUInt

  // DDR arbiter
  val ddrArbiter = Module(new BurstMemArbiter(3, Config.ddrConfig.addrWidth, Config.ddrConfig.dataWidth))
  ddrArbiter.connect(
    lineBufferDma.io.in.mapAddr(_ + pageFlipper.io.addrRead + lineBufferAddrOffset),
    frameBufferDma.io.out.mapAddr(_ + pageFlipper.io.addrWrite),
    queue.io.out.mapAddr(_ + pageFlipper.io.addrWrite)
  ) <> io.ddr

  // Outputs
  io.frameBuffer.mem <> queue.io.in
  io.frameBuffer.size := io.video.size
}
