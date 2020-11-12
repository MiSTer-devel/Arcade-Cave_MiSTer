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

class GPU extends Module {
  val io = IO(new Bundle {
    val generateFrame = Input(Bool())
    val bufferSelect = Input(Bool())
    val tileRom = new TileRomIO
    val spriteRam = ReadMemIO(Config.SPRITE_RAM_GPU_ADDR_WIDTH, Config.SPRITE_RAM_GPU_DATA_WIDTH)
    val layer0Ram = ReadMemIO(Config.LAYER_0_RAM_GPU_ADDR_WIDTH, Config.LAYER_0_RAM_GPU_DATA_WIDTH)
    val layer1Ram = ReadMemIO(Config.LAYER_1_RAM_GPU_ADDR_WIDTH, Config.LAYER_1_RAM_GPU_DATA_WIDTH)
    val layer2Ram = ReadMemIO(Config.LAYER_2_RAM_GPU_ADDR_WIDTH, Config.LAYER_2_RAM_GPU_DATA_WIDTH)
    val layer0Info = ReadMemIO(Config.LAYER_INFO_GPU_ADDR_WIDTH, Config.LAYER_INFO_GPU_DATA_WIDTH)
    val layer1Info = ReadMemIO(Config.LAYER_INFO_GPU_ADDR_WIDTH, Config.LAYER_INFO_GPU_DATA_WIDTH)
    val layer2Info = ReadMemIO(Config.LAYER_INFO_GPU_ADDR_WIDTH, Config.LAYER_INFO_GPU_DATA_WIDTH)
    val paletteRam = ReadMemIO(Config.PALETTE_RAM_GPU_ADDR_WIDTH, Config.PALETTE_RAM_GPU_DATA_WIDTH)
    val frameBuffer = new FrameBufferIO
  })

  class GPUBlackBox extends BlackBox {
    val io = IO(new Bundle {
      val clk_i = Input(Clock())
      val rst_i = Input(Reset())
      val generateFrame = Input(Bool())
      val bufferSelect = Input(Bool())
      val tileRom = new TileRomIO
      val spriteRam = ReadMemIO(Config.SPRITE_RAM_GPU_ADDR_WIDTH, Config.SPRITE_RAM_GPU_DATA_WIDTH)
      val layer0Ram = ReadMemIO(Config.LAYER_0_RAM_GPU_ADDR_WIDTH, Config.LAYER_0_RAM_GPU_DATA_WIDTH)
      val layer1Ram = ReadMemIO(Config.LAYER_1_RAM_GPU_ADDR_WIDTH, Config.LAYER_1_RAM_GPU_DATA_WIDTH)
      val layer2Ram = ReadMemIO(Config.LAYER_2_RAM_GPU_ADDR_WIDTH, Config.LAYER_2_RAM_GPU_DATA_WIDTH)
      val layer0Info = ReadMemIO(Config.LAYER_INFO_GPU_ADDR_WIDTH, Config.LAYER_INFO_GPU_DATA_WIDTH)
      val layer1Info = ReadMemIO(Config.LAYER_INFO_GPU_ADDR_WIDTH, Config.LAYER_INFO_GPU_DATA_WIDTH)
      val layer2Info = ReadMemIO(Config.LAYER_INFO_GPU_ADDR_WIDTH, Config.LAYER_INFO_GPU_DATA_WIDTH)
      val paletteRam = ReadMemIO(Config.PALETTE_RAM_GPU_ADDR_WIDTH, Config.PALETTE_RAM_GPU_DATA_WIDTH)
      val frameBuffer = new FrameBufferIO
    })

    override def desiredName = "graphic_processor"
  }

  val gpu = Module(new GPUBlackBox)
  gpu.io.clk_i := clock
  gpu.io.rst_i := reset
  gpu.io.tileRom <> io.tileRom
  gpu.io.spriteRam <> io.spriteRam
  gpu.io.layer0Ram <> io.layer0Ram
  gpu.io.layer1Ram <> io.layer1Ram
  gpu.io.layer2Ram <> io.layer2Ram
  gpu.io.layer0Info <> io.layer0Info
  gpu.io.layer1Info <> io.layer1Info
  gpu.io.layer2Info <> io.layer2Info
  gpu.io.paletteRam <> io.paletteRam
  gpu.io.frameBuffer <> io.frameBuffer
  gpu.io.generateFrame := io.generateFrame
  gpu.io.bufferSelect := io.bufferSelect
}
