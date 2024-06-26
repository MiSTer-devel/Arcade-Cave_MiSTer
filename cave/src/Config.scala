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

package cave

import arcadia.gfx.VideoTimingConfig
import arcadia.mem._
import arcadia.snd.{OKIM6295Config, YMZ280BConfig}
import chisel3.util.log2Ceil

object Config {
  /** System clock frequency (Hz) */
  val CLOCK_FREQ = 96_000_000D

  /** CPU clock frequency (Hz) */
  val CPU_CLOCK_FREQ = 32_000_000D
  /** CPU clock divider */
  val CPU_CLOCK_DIV = 2 // 16 MHz
  /** Clock period (ns) */
  val CPU_CLOCK_PERIOD = 1 / CPU_CLOCK_FREQ * 1_000_000_000D

  /** Sound clock divider */
  val SOUND_CLOCK_DIV = 8 // 4 MHz

  /** Video clock frequency (Hz) */
  val VIDEO_CLOCK_FREQ = 28_000_000D
  /** Video clock divider */
  val VIDEO_CLOCK_DIV = 4 // 7 MHz

  /** The width of the screen in pixels */
  val SCREEN_WIDTH = 320
  /** The height of the screen in pixels */
  val SCREEN_HEIGHT = 240

  /** The base address of the system frame buffer in DDR. */
  val SYSTEM_FRAME_BUFFER_BASE_ADDR = 0x24000000
  /** The base address of the sprite frame buffer in DDR. */
  val SPRITE_FRAME_BUFFER_BASE_ADDR = 0x24200000
  /** The base address of the IOCTL data stored in DDR. */
  val IOCTL_DOWNLOAD_BASE_ADDR = 0x30000000

  /** The number of DIPs registers */
  val DIPS_REGS_COUNT = 4

  /** The width of the pulse generated when a coin is inserted */
  val COIN_PULSE_WIDTH = (100_000_000D / CPU_CLOCK_PERIOD).ceil.toInt // 100 ms
  /** The width of the pulse generated when the service button is pressed */
  val SERVICE_PULSE_WIDTH = (2_500_000_000D / CPU_CLOCK_PERIOD).ceil.toInt // 2500 ms

  /** The width of the program ROM address bus */
  val PROG_ROM_ADDR_WIDTH = 20 // 1MB
  /** The width of the program ROM data bus */
  val PROG_ROM_DATA_WIDTH = 16

  /** The width of the EEPROM address bus */
  val EEPROM_ADDR_WIDTH = 7 // 256B
  /** The width of the EEPROM data bus */
  val EEPROM_DATA_WIDTH = 16

  /** The width of the sound ROM address bus */
  val SOUND_ROM_ADDR_WIDTH = 25
  /** The width of the sound ROM data bus */
  val SOUND_ROM_DATA_WIDTH = 8
  /** The number of sound ROMs */
  val SOUND_ROM_COUNT = 2

  /** The width of the tile ROM address bus */
  val TILE_ROM_ADDR_WIDTH = 32
  /** The width of the tile ROM data bus */
  val TILE_ROM_DATA_WIDTH = 64

  /** The width of the main RAM address bus */
  val MAIN_RAM_ADDR_WIDTH = 15
  /** The width of the main RAM address bus */
  val MAIN_RAM_DATA_WIDTH = 16

  /** The width of the sprite RAM address bus (CPU-side) */
  val SPRITE_RAM_ADDR_WIDTH = 15
  /** The width of the sprite RAM data bus (CPU-side) */
  val SPRITE_RAM_DATA_WIDTH = 16
  /** The width of the sprite RAM address bus (GPU-side) */
  val SPRITE_RAM_GPU_ADDR_WIDTH = 12
  /** The width of the sprite RAM data bus (GPU-side) */
  val SPRITE_RAM_GPU_DATA_WIDTH = 128

  /** The width of the 8x8 VRAM address bus (CPU-side) */
  val LAYER_8x8_RAM_ADDR_WIDTH = 13
  /** The width of the 8x8 VRAM address bus (GPU-side) */
  val LAYER_8x8_RAM_GPU_ADDR_WIDTH = 12
  /** The width of the 16x16 VRAM address bus (CPU-side) */
  val LAYER_16x16_RAM_ADDR_WIDTH = 11
  /** The width of the 16x16 VRAM address bus (GPU-side) */
  val LAYER_16x16_RAM_GPU_ADDR_WIDTH = 10
  /** The width of the VRAM data bus (CPU-side) */
  val LAYER_RAM_DATA_WIDTH = 16
  /** The width of the VRAM data bus (GPU-side) */
  val LAYER_RAM_GPU_DATA_WIDTH = 32

  /** The width of the line RAM address bus (CPU-side) */
  val LINE_RAM_ADDR_WIDTH = 10
  /** The width of the line RAM data bus (CPU-side) */
  val LINE_RAM_DATA_WIDTH = 16
  /** The width of the line RAM data bus (GPU-side) */
  val LINE_RAM_GPU_ADDR_WIDTH = 9
  /** The width of the line RAM data bus (GPU-side) */
  val LINE_RAM_GPU_DATA_WIDTH = 32

  /** The width of the palette RAM address bus (CPU-side) */
  val PALETTE_RAM_ADDR_WIDTH = 15
  /** The width of the palette RAM data bus (CPU-side) */
  val PALETTE_RAM_DATA_WIDTH = 16
  /** The width of the palette RAM address bus (GPU-side) */
  val PALETTE_RAM_GPU_ADDR_WIDTH = 15
  /** The width of the palette RAM data bus (GPU-side) */
  val PALETTE_RAM_GPU_DATA_WIDTH = 16

  /** The number of tilemap layers */
  val LAYER_COUNT = 3
  /** The number of layer registers */
  val LAYER_REGS_COUNT = 3
  /** The number of sprite registers */
  val SPRITE_REGS_COUNT = 8

  /** The width of the final RGB values */
  val RGB_WIDTH = 24

  /** The width of the frame buffer in pixels */
  val FRAME_BUFFER_WIDTH = 512
  /** The height of the frame buffer in pixels */
  val FRAME_BUFFER_HEIGHT = 256
  /** The depth of the frame buffer in words */
  val FRAME_BUFFER_DEPTH = FRAME_BUFFER_WIDTH * FRAME_BUFFER_HEIGHT
  /** The width of the frame buffer X address */
  val FRAME_BUFFER_ADDR_WIDTH_X = log2Ceil(FRAME_BUFFER_WIDTH)
  /** The width of the frame buffer Y address */
  val FRAME_BUFFER_ADDR_WIDTH_Y = log2Ceil(FRAME_BUFFER_HEIGHT)
  /** The width of the frame buffer address bus */
  val FRAME_BUFFER_ADDR_WIDTH = log2Ceil(FRAME_BUFFER_DEPTH)
  /** The width of the system frame buffer data bus */
  val SYSTEM_FRAME_BUFFER_DATA_WIDTH = 32
  /** The depth of the system frame buffer request queue */
  val SYSTEM_FRAME_BUFFER_REQUEST_QUEUE_DEPTH = 16
  /** The width of the sprite frame buffer data bus */
  val SPRITE_FRAME_BUFFER_DATA_WIDTH = 16
  /** The depth of the sprite frame buffer request queue */
  val SPRITE_FRAME_BUFFER_REQUEST_QUEUE_DEPTH = 16
  /** The width of the sprite frame buffer in bytes */
  val SPRITE_FRAME_BUFFER_STRIDE = FRAME_BUFFER_WIDTH * SPRITE_FRAME_BUFFER_DATA_WIDTH / 8

  /** The width of a priority value */
  val PRIO_WIDTH = 2
  /** The width of a color code value */
  val PALETTE_WIDTH = 6
  /** The width of palette index (256 colors) */
  val COLOR_WIDTH = 8
  /** The width of the layer scroll value */
  val LAYER_SCROLL_WIDTH = 9

  /** The size of a sprite tile in pixels */
  val SPRITE_TILE_SIZE = 16
  /** The maximum bit depth of a tile */
  val SPRITE_TILE_MAX_BPP = 8

  /** The width of audio sample values */
  val AUDIO_SAMPLE_WIDTH = 16

  /** DDR configuration */
  val ddrConfig = ddr.Config()

  /** SDRAM configuration */
  val sdramConfig = sdram.Config(clockFreq = CLOCK_FREQ, burstLength = 4)

  /** Original (57Hz) video timing configuration */
  val originalVideoTimingConfig = VideoTimingConfig(
    clockFreq = VIDEO_CLOCK_FREQ,
    clockDiv = VIDEO_CLOCK_DIV,
    hFreq = 15_625, // Hz
    vFreq = 57.444 // Hz
  )

  /** Compatibility (60Hz) video timing configuration */
  val compatibilityVideoTimingConfig = VideoTimingConfig(
    clockFreq = VIDEO_CLOCK_FREQ,
    clockDiv = VIDEO_CLOCK_DIV,
    hFreq = 15_734, // Hz (Match NTSC SPEC)
    vFreq = 60 // Hz
  )

  /** Copy download DMA configuration */
  val copyDownloadDmaConfig = dma.Config(depth = 0x400000, burstLength = 16) // 32 MB

  /** Sprite frame buffer DMA configuration */
  val spriteFrameBufferDmaConfig = dma.Config(
    depth = FRAME_BUFFER_WIDTH * FRAME_BUFFER_HEIGHT * SPRITE_FRAME_BUFFER_DATA_WIDTH / ddrConfig.dataWidth,
    burstLength = 64
  )

  /** Sprite line buffer DMA configuration */
  val spriteLineBufferDmaConfig = dma.Config(
    depth = FRAME_BUFFER_WIDTH * SPRITE_FRAME_BUFFER_DATA_WIDTH / ddrConfig.dataWidth,
    burstLength = 16
  )

  /** YMZ280B configuration */
  val ymzConfig = YMZ280BConfig(clockFreq = CPU_CLOCK_FREQ, sampleFreq = 88_200)

  /** OKIM6295 configuration */
  val okiConfig = Seq(
    OKIM6295Config(clockFreq = CPU_CLOCK_FREQ, sampleFreq = 1_056_000),
    OKIM6295Config(clockFreq = CPU_CLOCK_FREQ, sampleFreq = 2_112_000)
  )
}
