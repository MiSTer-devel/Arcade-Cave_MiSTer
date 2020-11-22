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

import axon.mem._
import cave.Config
import cave.types._
import chisel3._

class LayerProcessor extends Module {
  /** The width of the layer index value */
  val LAYER_INDEX_WIDTH = 2

  val io = IO(new Bundle {
    /** Start flag */
    val start = Input(Bool())
    /** Done flag */
    val done = Output(Bool())
    /** Layer index */
    val layerIndex = Input(UInt(LAYER_INDEX_WIDTH.W))
    /** Layer registers port */
    val layerRegs = Input(Bits(Config.LAYER_REGS_GPU_DATA_WIDTH.W))
    /** Layer RAM port */
    val layerRam = ReadMemIO(Config.LAYER_RAM_GPU_ADDR_WIDTH, Config.LAYER_RAM_GPU_DATA_WIDTH)
    /** Palette RAM port */
    val paletteRam = ReadMemIO(Config.PALETTE_RAM_GPU_ADDR_WIDTH, Config.PALETTE_RAM_GPU_DATA_WIDTH)
    /** Tile ROM port */
    val tileRom = new TileRomIO
    /** Priority port */
    val priority = new PriorityIO
    /** Frame buffer port */
    val frameBuffer = WriteMemIO(Config.FRAME_BUFFER_ADDR_WIDTH, Config.FRAME_BUFFER_DATA_WIDTH)
  })

  class LayerProcessorBlackBox extends BlackBox {
    val io = IO(new Bundle {
      val clk_i = Input(Clock())
      val rst_i = Input(Reset())
      val start_i = Input(Bool())
      val done_o = Output(Bool())
      val layer_number_i = Input(UInt(LAYER_INDEX_WIDTH.W))
      val layer_info_i = Input(Bits(Config.LAYER_REGS_GPU_DATA_WIDTH.W))
      val layerRam = ReadMemIO(Config.LAYER_RAM_GPU_ADDR_WIDTH, Config.LAYER_RAM_GPU_DATA_WIDTH)
      val paletteRam = ReadMemIO(Config.PALETTE_RAM_GPU_ADDR_WIDTH, Config.PALETTE_RAM_GPU_DATA_WIDTH)
      val tileRom = new TileRomIO
      val priority = new PriorityIO
      val frameBuffer = WriteMemIO(Config.FRAME_BUFFER_ADDR_WIDTH, Config.FRAME_BUFFER_DATA_WIDTH)
    })

    override def desiredName = "layer_processor"
  }

  val processor = Module(new LayerProcessorBlackBox)
  processor.io.clk_i := clock
  processor.io.rst_i := reset
  processor.io.start_i := io.start
  io.done := processor.io.done_o
  processor.io.layer_number_i := io.layerIndex
  processor.io.layer_info_i := io.layerRegs
  processor.io.layerRam <> io.layerRam
  processor.io.paletteRam <> io.paletteRam
  processor.io.tileRom <> io.tileRom
  processor.io.priority <> io.priority
  processor.io.frameBuffer <> io.frameBuffer
}
