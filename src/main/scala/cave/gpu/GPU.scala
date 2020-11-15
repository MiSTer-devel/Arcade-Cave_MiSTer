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

import axon.mem.ReadMemIO
import cave._
import cave.types._
import chisel3._

/** Graphics Processor */
class GPU extends Module {
  val io = IO(new Bundle {
    /** Strobe to indicate that a new frame should be generated */
    val generateFrame = Input(Bool())
    /** Video registers port */
    val videoRegs = Input(Bits(Config.VIDEO_GPU_DATA_WIDTH.W))
    /** Layer 0 registers port */
    val layer0Regs = Input(Bits(Config.LAYER_GPU_DATA_WIDTH.W))
    /** Layer 1 registers port */
    val layer1Regs = Input(Bits(Config.LAYER_GPU_DATA_WIDTH.W))
    /** Layer 2 registers port */
    val layer2Regs = Input(Bits(Config.LAYER_GPU_DATA_WIDTH.W))
    /** Tile ROM port */
    val tileRom = new TileRomIO
    /** Sprite RAM port */
    val spriteRam = ReadMemIO(Config.SPRITE_RAM_GPU_ADDR_WIDTH, Config.SPRITE_RAM_GPU_DATA_WIDTH)
    /** Layer 0 RAM port */
    val layer0Ram = ReadMemIO(Config.LAYER_0_RAM_GPU_ADDR_WIDTH, Config.LAYER_0_RAM_GPU_DATA_WIDTH)
    /** Layer 1 RAM port */
    val layer1Ram = ReadMemIO(Config.LAYER_1_RAM_GPU_ADDR_WIDTH, Config.LAYER_1_RAM_GPU_DATA_WIDTH)
    /** Layer 2 RAM port */
    val layer2Ram = ReadMemIO(Config.LAYER_2_RAM_GPU_ADDR_WIDTH, Config.LAYER_2_RAM_GPU_DATA_WIDTH)
    /** Palette RAM port */
    val paletteRam = ReadMemIO(Config.PALETTE_RAM_GPU_ADDR_WIDTH, Config.PALETTE_RAM_GPU_DATA_WIDTH)
    /** Frame buffer port */
    val frameBuffer = new FrameBufferIO
  })

  class GPUBlackBox extends BlackBox {
    val io = IO(new Bundle {
      val clk_i = Input(Clock())
      val rst_i = Input(Reset())
      val generateFrame = Input(Bool())
      val spriteBank = Input(Bool())
      val layer0Info = Input(Bits(Config.LAYER_GPU_DATA_WIDTH.W))
      val layer1Info = Input(Bits(Config.LAYER_GPU_DATA_WIDTH.W))
      val layer2Info = Input(Bits(Config.LAYER_GPU_DATA_WIDTH.W))
      val tileRom = new TileRomIO
      val spriteRam = ReadMemIO(Config.SPRITE_RAM_GPU_ADDR_WIDTH, Config.SPRITE_RAM_GPU_DATA_WIDTH)
      val layer0Ram = ReadMemIO(Config.LAYER_0_RAM_GPU_ADDR_WIDTH, Config.LAYER_0_RAM_GPU_DATA_WIDTH)
      val layer1Ram = ReadMemIO(Config.LAYER_1_RAM_GPU_ADDR_WIDTH, Config.LAYER_1_RAM_GPU_DATA_WIDTH)
      val layer2Ram = ReadMemIO(Config.LAYER_2_RAM_GPU_ADDR_WIDTH, Config.LAYER_2_RAM_GPU_DATA_WIDTH)
      val paletteRam = ReadMemIO(Config.PALETTE_RAM_GPU_ADDR_WIDTH, Config.PALETTE_RAM_GPU_DATA_WIDTH)
      val frameBuffer = new FrameBufferIO
    })

    override def desiredName = "graphic_processor"
  }

  // Set the sprite bank
  val spriteBank = io.videoRegs(4)(0)

  val gpu = Module(new GPUBlackBox)
  gpu.io.clk_i := clock
  gpu.io.rst_i := reset
  gpu.io.generateFrame := io.generateFrame
  gpu.io.spriteBank := spriteBank
  gpu.io.layer0Info <> io.layer0Regs
  gpu.io.layer1Info <> io.layer1Regs
  gpu.io.layer2Info <> io.layer2Regs
  gpu.io.tileRom <> io.tileRom
  gpu.io.spriteRam <> io.spriteRam
  gpu.io.layer0Ram <> io.layer0Ram
  gpu.io.layer1Ram <> io.layer1Ram
  gpu.io.layer2Ram <> io.layer2Ram
  gpu.io.paletteRam <> io.paletteRam
  gpu.io.frameBuffer <> io.frameBuffer
}
