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

import axon.mem._
import chisel3._

package object types {
  /** Cache IO */
  class CacheIO extends ValidReadMemIO(Config.CACHE_ADDR_WIDTH, Config.CACHE_DATA_WIDTH) {
    override def cloneType: this.type = new CacheIO().asInstanceOf[this.type]
  }

  /** Frame buffer IO */
  class FrameBufferIO extends ReadMemIO(Config.FRAME_BUFFER_ADDR_WIDTH-2, Config.FRAME_BUFFER_DATA_WIDTH*4) {
    override def cloneType: this.type = new FrameBufferIO().asInstanceOf[this.type]
  }

  /** Player IO */
  class PlayerIO extends Bundle {
    /** Player 1 input */
    val player1 = Input(Bits(9.W))
    /** Player 2 input */
    val player2 = Input(Bits(9.W))
    /** Pause flag */
    val pause = Input(Bool())
  }

  /** Priority IO */
  class PriorityIO extends Bundle {
    /** Write-only port */
    val write = WriteMemIO(Config.FRAME_BUFFER_ADDR_WIDTH, Config.FRAME_BUFFER_PRIO_WIDTH)
    /** Read-only port */
    val read = ReadMemIO(Config.FRAME_BUFFER_ADDR_WIDTH, Config.FRAME_BUFFER_PRIO_WIDTH)
  }

  /** Program ROM IO */
  class ProgRomIO extends ValidReadMemIO(Config.PROG_ROM_ADDR_WIDTH, Config.PROG_ROM_DATA_WIDTH)

  /** Sound ROM IO */
  class SoundRomIO extends ValidReadMemIO(Config.SOUND_ROM_ADDR_WIDTH, Config.SOUND_ROM_DATA_WIDTH)

  /** Tile ROM IO */
  class TileRomIO extends BurstReadMemIO(Config.TILE_ROM_ADDR_WIDTH, Config.TILE_ROM_DATA_WIDTH) {
    override def cloneType: this.type = new TileRomIO().asInstanceOf[this.type]
  }
}
