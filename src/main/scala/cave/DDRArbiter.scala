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
import axon.types._
import chisel3._
import chisel3.util._

/**
 * A DDR memory arbiter.
 *
 * The DDR memory arbiter routes requests from multiple input ports to a single output port.
 */
class DDRArbiter extends Module {
  val io = IO(new Bundle {
    /** Download port */
    val download = DownloadIO()
    /** Program ROM port */
    val progRom = Flipped(BurstReadWriteMemIO(Config.DDR_ADDR_WIDTH, Config.DDR_DATA_WIDTH))
    /** Sound ROM port */
    val soundRom = Flipped(BurstReadWriteMemIO(Config.DDR_ADDR_WIDTH, Config.DDR_DATA_WIDTH))
    /** Tile ROM port */
    val tileRom = Flipped(BurstReadMemIO(Config.DDR_ADDR_WIDTH, Config.DDR_DATA_WIDTH))
    /** Frame buffer to DDR port */
    val fbToDDR = Flipped(BurstWriteMemIO(Config.DDR_ADDR_WIDTH, Config.DDR_DATA_WIDTH))
    /** Frame buffer from DDR port */
    val fbFromDDR = Flipped(BurstReadMemIO(Config.DDR_ADDR_WIDTH, Config.DDR_DATA_WIDTH))
    /** DDR port */
    val ddr = BurstReadWriteMemIO(Config.DDR_ADDR_WIDTH, Config.DDR_DATA_WIDTH)
  })

  val cache = Module(new CacheMem(CacheConfig(
    inAddrWidth = DownloadIO.ADDR_WIDTH,
    inDataWidth = DownloadIO.DATA_WIDTH,
    outAddrWidth = Config.DDR_ADDR_WIDTH,
    outDataWidth = Config.DDR_DATA_WIDTH,
    lineWidth = 1,
    depth = 1
  )))
  cache.io.in <> io.download.asAsyncReadWriteMemIO

  // Arbiter
  val arbiter = Module(new MemArbiter(6, Config.DDR_ADDR_WIDTH, Config.DDR_DATA_WIDTH))
  arbiter.io.in(0) <> cache.io.out
  arbiter.io.in(1).asBurstReadMemIO <> io.fbFromDDR // high-priority required to burst data to the video FIFO
  arbiter.io.in(2).asBurstWriteMemIO <> io.fbToDDR
  arbiter.io.in(3).asBurstReadMemIO <> io.tileRom
  arbiter.io.in(4) <> io.progRom
  arbiter.io.in(5) <> io.soundRom
  arbiter.io.out <> io.ddr
}
