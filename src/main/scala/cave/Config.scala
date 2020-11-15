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

package cave

import axon.gpu.VideoTimingConfig

object Config {
  /** The system clock frequency (Hz) */
  val CLOCK_FREQ = 96000000D

  val SCREEN_WIDTH = 320
  val SCREEN_HEIGHT = 240

  val CACHE_ADDR_WIDTH = 20
  val CACHE_DATA_WIDTH = 256

  val PROG_ROM_ADDR_WIDTH = 24
  val PROG_ROM_DATA_WIDTH = 16
  val PROG_ROM_OFFSET = 0x000000

  val TILE_ROM_ADDR_WIDTH = 32
  val TILE_ROM_DATA_WIDTH = 64
  val TILE_ROM_OFFSET = 0x100000

  val MAIN_RAM_ADDR_WIDTH = 15
  val MAIN_RAM_DATA_WIDTH = 16

  val SPRITE_RAM_ADDR_WIDTH = 15
  val SPRITE_RAM_DATA_WIDTH = 16
  val SPRITE_RAM_GPU_ADDR_WIDTH = 12
  val SPRITE_RAM_GPU_DATA_WIDTH = 128

  val LAYER_0_RAM_ADDR_WIDTH = 14
  val LAYER_0_RAM_DATA_WIDTH = 16
  val LAYER_0_RAM_GPU_ADDR_WIDTH = 13
  val LAYER_0_RAM_GPU_DATA_WIDTH = 32

  val LAYER_1_RAM_ADDR_WIDTH = 14
  val LAYER_1_RAM_DATA_WIDTH = 16
  val LAYER_1_RAM_GPU_ADDR_WIDTH = 13
  val LAYER_1_RAM_GPU_DATA_WIDTH = 32

  val LAYER_2_RAM_ADDR_WIDTH = 12
  val LAYER_2_RAM_DATA_WIDTH = 16
  val LAYER_2_RAM_GPU_ADDR_WIDTH = 11
  val LAYER_2_RAM_GPU_DATA_WIDTH = 32

  val PALETTE_RAM_ADDR_WIDTH = 15
  val PALETTE_RAM_DATA_WIDTH = 16
  val PALETTE_RAM_GPU_ADDR_WIDTH = 15
  val PALETTE_RAM_GPU_DATA_WIDTH = 16

  val LAYER_NUM_REGS = 3
  val LAYER_GPU_DATA_WIDTH = 48

  val VIDEO_NUM_REGS = 8
  val VIDEO_GPU_DATA_WIDTH = 128

  val SOUND_NUM_REGS = 4

  val FRAME_BUFFER_ADDR_WIDTH = 17
  val FRAME_BUFFER_DATA_WIDTH = 15

  /** Video timing configuration */
  val videoTimingConfig = VideoTimingConfig(
    hDisplay = 320,
    hFrontPorch = 5,
    hRetrace = 23,
    hBackPorch = 34,
    vDisplay = 240,
    vFrontPorch = 12,
    vRetrace = 2,
    vBackPorch = 19
  )
}
