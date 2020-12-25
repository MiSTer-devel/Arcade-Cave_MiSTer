/*
 *    __   __     __  __     __         __
 *   /\ "-.\ \   /\ \/\ \   /\ \       /\ \
 *   \ \ \-.  \  \ \ \_\ \  \ \ \____  \ \ \____
 *    \ \_\\"\_\  \ \_____\  \ \_____\  \ \_____\
 *     \/_/ \/_/   \/_____/   \/_____/   \/_____/
 *    ______     ______       __     ______     ______     ______
 *   /\  __ \   /\  == \     /\ \   /\  ___\   /\  ___\   /\__  _\
 *   \ \ \/\ \  \ \  __<    _\_\ \  \ \  __\   \ \ \____  \/_/\ \/
 *    \ \_____\  \ \_____\ /\_____\  \ \_____\  \ \_____\    \ \_\
 *     \/_____/   \/_____/ \/_____/   \/_____/   \/_____/     \/_/
 *
 *  https://joshbassett.info
 *  https://twitter.com/nullobject
 *  https://github.com/nullobject
 *
 *  Copyright (c) 2020 Josh Bassett
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
 */

package cave.gpu

import axon.Util
import axon.mem._
import axon.types._
import axon.util.Counter
import cave.Config
import cave.types._
import chisel3._
import chisel3.util._

/** The sprite blitter copies a sprite to the frame buffer. */
class SpriteBlitter extends Module {
  val io = IO(new Bundle {
    /** Sprite data port */
    val spriteData = DeqIO(new Sprite)
    /** Pixel data port */
    val pixelData = DeqIO(Bits(Config.TILE_ROM_DATA_WIDTH.W))
    /** Palette RAM port */
    val paletteRam = ReadMemIO(Config.PALETTE_RAM_GPU_ADDR_WIDTH, Config.PALETTE_RAM_GPU_DATA_WIDTH)
    /** Priority port */
    val priority = new PriorityIO
    /** Frame buffer port */
    val frameBuffer = WriteMemIO(Config.FRAME_BUFFER_ADDR_WIDTH, Config.FRAME_BUFFER_DATA_WIDTH)
    /** Done flag */
    val done = Output(Bool())
  })

  // Wires
  val updateSpriteInfo = Wire(Bool())
  val pisoEmpty = Wire(Bool())
  val pisoAlmostEmpty = Wire(Bool())
  val readFifo = Wire(Bool())
  val spriteDone = Wire(Bool())

  // Registers
  val spriteInfoReg = RegEnable(io.spriteData.bits, updateSpriteInfo)
  val paletteReg = Reg(new PaletteColorSelect)
  val pisoReg = Reg(Vec(Config.LARGE_TILE_SIZE, UInt(Config.LARGE_TILE_BPP.W)))
  val pisoCounterReg = RegInit(0.U)

  // Counters
  val (x, xWrap) = Counter.dynamic(spriteInfoReg.size.x, enable = !pisoEmpty)
  val (y, yWrap) = Counter.dynamic(spriteInfoReg.size.y, enable = xWrap)

  // Tile pixel position
  val tilePixelPos = {
    val destX = Mux(spriteInfoReg.flipX, spriteInfoReg.size.x - x - 1.U, x)
    val destY = Mux(spriteInfoReg.flipY, spriteInfoReg.size.y - y - 1.U, y)
    spriteInfoReg.pos + SVec2(destX.asSInt, destY.asSInt)
  }
  val stage1Pos = RegNext(tilePixelPos)
  val stage2Pos = RegNext(stage1Pos)

  // Update PISO counter register
  when(readFifo) {
    pisoCounterReg := Config.LARGE_TILE_SIZE.U
  }.elsewhen(!pisoEmpty) {
    pisoCounterReg := pisoCounterReg - 1.U
  }

  // Set PISO empty flags
  pisoEmpty := pisoCounterReg === 0.U
  pisoAlmostEmpty := pisoCounterReg === 1.U

  // The FIFO can only be read when it is not empty and should be read if the PISO is empty or will
  // be empty next clock cycle. Since the pipeline after the FIFO has no backpressure and can
  // accommodate data every clock cycle, this will be the case if the PISO counter is 1.
  readFifo := io.pixelData.valid && (pisoEmpty || pisoAlmostEmpty)

  // The sprite info should be updated when we read a new sprite from the FIFO, this can be in
  // either of two cases. First, when the counters are at 0 (no data yet) and the first pixels
  // arrive, second, when a sprite finishes and the data for the second sprite is already there.
  //
  // This is to achieve maximum efficiency of the pipeline, while there are sprites to draw we burst
  // them from memory into the pipeline. Max one 16x16 tile in advance, so there are at most 2 16x16
  // tiles in the pipeline at any given time.
  //
  // When there is space in the pipeline for a 16x16 tile and there are sprites to draw, a tile will
  // be bursted from memory into the pipeline. A 16x16 tile is 16 bytes since a byte encodes 2 pixel
  // colors on nibbles, these are 16 color sprites.
  updateSpriteInfo := readFifo && ((x === 0.U && y === 0.U) || (xWrap && yWrap))

  // The sprite has been blitted when the counters are at max values and the PISO is not empty.
  spriteDone := xWrap && yWrap && !pisoEmpty

  // The sprites use the first 64 palettes, and use 16 colors (out of 256 possible in a palette)
  paletteReg := PaletteColorSelect(spriteInfoReg.colorCode, pisoReg.head)

  // Decode pixel data into the PISO
  when(readFifo) {
    pisoReg := VecInit(SpriteBlitter.decodePixelData(io.pixelData.bits))
  }.otherwise {
    pisoReg := pisoReg.tail :+ pisoReg.head
  }

  // The CAVE first-generation hardware handles transparency the following way:
  //
  // If the color code (palette index) is 0 the pixel is transparent. This results in 15 usable
  // colors for a tile. Even if the color at the first index of the palette is not zero, the pixel
  // still is transparent.
  //
  // It is difficult to understand why CAVE didn't use the MSB bit of the 16 bit color word to
  // indicate transparency. This would allow for 16 colors out of 2^15 colors for each tile instead
  // of 15 colors of 2^15. With the cave CV1000 (SH3) hardware, they use the MSB bit of the 16-bit
  // word as transparency bit while the colors remain RGB555.
  //
  // One wonders why they didn't do this on first-generation hardware. The transparency info must be
  // delayed by one cycle, as for the colors (since the colors come from the palette RAM (BRAM) they
  // arrive one cycle later).
  val isTransparent = RegNext(paletteReg.colorIndex === 0.U)

  // Set valid flag
  val valid = ShiftRegister(!pisoEmpty, 2, false.B, true.B)

  // Set done flag
  val done = ShiftRegister(spriteDone, 2, false.B, true.B)

  // Calculate priority
  val priorityReadAddr = stage1Pos.x(Config.FRAME_BUFFER_ADDR_WIDTH_X - 1, 0) ##
                         stage1Pos.y(Config.FRAME_BUFFER_ADDR_WIDTH_Y - 1, 0)
  val priorityWriteData = ShiftRegister(spriteInfoReg.priority, 2)

  // The current sprite has priority if it has more priority or the same priority as the previous
  // sprite (all priority should be 0 at start).
  val hasPriority = priorityWriteData >= io.priority.read.dout

  // Calculate sprite visibility
  val visible = stage2Pos.x >= 0.S &&
                stage2Pos.y >= 0.S &&
                stage2Pos.x < Config.SCREEN_WIDTH.S &&
                stage2Pos.y < Config.SCREEN_HEIGHT.S

  // Calculate frame buffer data
  val frameBufferWrite = valid && hasPriority && !isTransparent && visible
  val frameBufferAddr = stage2Pos.x(Config.FRAME_BUFFER_ADDR_WIDTH_X - 1, 0) ##
                        stage2Pos.y(Config.FRAME_BUFFER_ADDR_WIDTH_Y - 1, 0)
  val frameBufferData = GPU.decodePaletteData(io.paletteRam.dout)

  // Outputs
  io.spriteData.ready := updateSpriteInfo
  io.pixelData.ready := readFifo
  io.paletteRam.rd := true.B
  io.paletteRam.addr := paletteReg.asUInt
  io.priority.read.rd := true.B
  io.priority.read.addr := priorityReadAddr
  io.priority.write.wr := frameBufferWrite
  io.priority.write.addr := frameBufferAddr
  io.priority.write.mask := 0.U
  io.priority.write.din := priorityWriteData
  io.frameBuffer.wr := frameBufferWrite
  io.frameBuffer.addr := frameBufferAddr
  io.frameBuffer.mask := 0.U
  io.frameBuffer.din := frameBufferData.asUInt
  io.done := done
}

object SpriteBlitter {
  /**
   * Decodes the pixel data into a sequence of tile rows.
   *
   * The sprite tiles are encoded in the following sequence:
   *
   * {{{
   * 3, 2, 1, 0, 7, 6, 5, 4, 11, 10, 9, 8, 15, 14, 13, 12
   * }}}
   *
   * @param data The pixel data.
   */
  def decodePixelData(data: Bits): Seq[Bits] =
    Util
      .decode(data, Config.LARGE_TILE_SIZE, Config.LARGE_TILE_BPP)
      .grouped(4)
      .flatMap(_.reverse)
      .toSeq
}
