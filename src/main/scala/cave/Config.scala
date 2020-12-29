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
import axon.mem.{DDRConfig, SDRAMConfig}
import axon.snd.YMZ280BConfig
import chisel3.util.log2Ceil

object Config {
  /** System clock frequency (Hz) */
  val CLOCK_FREQ = 96000000

  /** CPU clock frequency (Hz) */
  val CPU_CLOCK_FREQ = 32000000

  /** Sample frequency (Hz) */
  val SAMPLE_FREQ = 88200

  val SAMPLE_WIDTH = 16

  val PLAYER_DATA_WIDTH = 10

  val SCREEN_WIDTH = 320
  val SCREEN_HEIGHT = 240
  /** The number of bits per color channel */
  val BITS_PER_CHANNEL = 5

  val SPRITE_CODE_WIDTH = 18
  val SPRITE_POS_WIDTH = 10
  val SPRITE_TILE_SIZE_WIDTH = 8
  val SPRITE_ZOOM_WIDTH = 16

  val PROG_ROM_OFFSET = 0x0000000
  val TILE_ROM_OFFSET = 0x0100000
  val SOUND_ROM_OFFSET = 0x0f00000
  val FRAME_BUFFER_OFFSET = 0x2000000

  // Layer offsets
  val SPRITE_ROM_OFFSET = 0x0000000
  val LAYER_0_ROM_OFFSET = 0x0800000
  val LAYER_1_ROM_OFFSET = 0x0a00000
  val LAYER_2_ROM_OFFSET = 0x0c00000

  val PROG_ROM_ADDR_WIDTH = 20 // 1MB
  val PROG_ROM_DATA_WIDTH = 16

  val SOUND_ROM_ADDR_WIDTH = 25
  val SOUND_ROM_DATA_WIDTH = 8

  val TILE_ROM_ADDR_WIDTH = 32
  val TILE_ROM_DATA_WIDTH = 64

  val MAIN_RAM_ADDR_WIDTH = 15
  val MAIN_RAM_DATA_WIDTH = 16

  val SPRITE_RAM_ADDR_WIDTH = 15
  val SPRITE_RAM_DATA_WIDTH = 16
  val SPRITE_RAM_GPU_ADDR_WIDTH = 12
  val SPRITE_RAM_GPU_DATA_WIDTH = 128

  val LAYER_RAM_GPU_ADDR_WIDTH = 13
  val LAYER_RAM_GPU_DATA_WIDTH = 32

  val LAYER_0_RAM_ADDR_WIDTH = 14
  val LAYER_0_RAM_DATA_WIDTH = 16
  val LAYER_0_RAM_GPU_ADDR_WIDTH = 13
  val LAYER_0_RAM_GPU_DATA_WIDTH = 32

  val LAYER_1_RAM_ADDR_WIDTH = 14
  val LAYER_1_RAM_DATA_WIDTH = 16
  val LAYER_1_RAM_GPU_ADDR_WIDTH = 13
  val LAYER_1_RAM_GPU_DATA_WIDTH = 32

  val LAYER_2_RAM_ADDR_WIDTH = 13
  val LAYER_2_RAM_DATA_WIDTH = 16
  val LAYER_2_RAM_GPU_ADDR_WIDTH = 12
  val LAYER_2_RAM_GPU_DATA_WIDTH = 32

  val PALETTE_RAM_ADDR_WIDTH = 15
  val PALETTE_RAM_DATA_WIDTH = 16
  val PALETTE_RAM_GPU_ADDR_WIDTH = 15
  val PALETTE_RAM_GPU_DATA_WIDTH = 16

  val NUM_PALETTES = 128
  val NUM_COLORS = 256
  val PALETTE_ENTRY_PALLETE_WIDTH = log2Ceil(NUM_PALETTES)
  val PALETTE_ENTRY_COLOR_WIDTH = log2Ceil(NUM_COLORS)

  val LAYER_REGS_COUNT = 3
  val LAYER_REGS_GPU_DATA_WIDTH = 48

  val VIDEO_REGS_COUNT = 8
  val VIDEO_REGS_GPU_DATA_WIDTH = 128

  val DDR_FRAME_BUFFER_BPP = 16

  val FRAME_BUFFER_ADDR_WIDTH_X = log2Ceil(SCREEN_WIDTH)
  val FRAME_BUFFER_ADDR_WIDTH_Y = log2Ceil(SCREEN_HEIGHT)
  val FRAME_BUFFER_ADDR_WIDTH = FRAME_BUFFER_ADDR_WIDTH_X + FRAME_BUFFER_ADDR_WIDTH_Y
  val FRAME_BUFFER_DATA_WIDTH = 15
  val FRAME_BUFFER_DEPTH = SCREEN_WIDTH * SCREEN_HEIGHT
  val FRAME_BUFFER_DMA_ADDR_WIDTH = 15
  val FRAME_BUFFER_DMA_DATA_WIDTH = 60
  val FRAME_BUFFER_DMA_DEPTH = SCREEN_WIDTH * SCREEN_HEIGHT / 4
  val FRAME_BUFFER_DMA_NUM_WORDS = SCREEN_WIDTH * SCREEN_HEIGHT * DDR_FRAME_BUFFER_BPP / 64
  val FRAME_BUFFER_DMA_BURST_LENGTH = 128

  /** The width of a priority value */
  val PRIO_WIDTH = 2
  /** The width of a color code value */
  val COLOR_CODE_WIDTH = 6
  /** The width of the layer index value */
  val LAYER_INDEX_WIDTH = 2
  /** The width of the layer scroll value */
  val LAYER_SCROLL_WIDTH = 9

  /** The bit depth of a small tile pixel */
  val SMALL_TILE_BPP = 8
  /** The size of a small tile in pixels */
  val SMALL_TILE_SIZE = 8
  /** The size of a large tile in bytes */
  val SMALL_TILE_BYTE_SIZE = SMALL_TILE_SIZE * 8
  /** The number of small tiles that fit horizontally on the screen */
  val SMALL_TILE_NUM_COLS = SCREEN_WIDTH / SMALL_TILE_SIZE + 1
  /** The number of small tiles that fit vertically on the screen */
  val SMALL_TILE_NUM_ROWS = SCREEN_HEIGHT / SMALL_TILE_SIZE + 1
  /** The number of small tiles that fit on the screen */
  val SMALL_TILE_NUM_TILES = SMALL_TILE_NUM_COLS * SMALL_TILE_NUM_ROWS

  /** The bit depth of a large tile pixel */
  val LARGE_TILE_BPP = 4
  /** The size of a large tile in pixels */
  val LARGE_TILE_SIZE = 16
  /** The size of a large tile in bytes */
  val LARGE_TILE_BYTE_SIZE = LARGE_TILE_SIZE * 8
  /** The number of large tiles that fit horizontally on the screen */
  val LARGE_TILE_NUM_COLS = SCREEN_WIDTH / LARGE_TILE_SIZE + 1
  /** The number of large tiles that fit vertically on the screen */
  val LARGE_TILE_NUM_ROWS = SCREEN_HEIGHT / LARGE_TILE_SIZE + 1
  /** The number of large tiles that fit on the screen */
  val LARGE_TILE_NUM_TILES = LARGE_TILE_NUM_COLS * LARGE_TILE_NUM_ROWS

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

  /** YMZ280B configuration */
  val ymzConfig = YMZ280BConfig(
    clockFreq = CPU_CLOCK_FREQ,
    sampleFreq = SAMPLE_FREQ,
    sampleWidth = SAMPLE_WIDTH
  )

  /** DDR configuration */
  val ddrConfig = DDRConfig()

  /** SDRAM configuration */
  val sdramConfig = SDRAMConfig(clockFreq = CLOCK_FREQ, burstLength = 4)
}
