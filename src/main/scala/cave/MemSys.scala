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

/** The memory subsystem routes various memory ports to either DDR or SDRAM. */
class MemSys extends Module {
  val io = IO(new Bundle {
    /** Download port */
    val download = DownloadIO()
    /** Program ROM port */
    val progRom = Flipped(AsyncReadWriteMemIO(Config.PROG_ROM_ADDR_WIDTH, Config.PROG_ROM_DATA_WIDTH))
    /** Sound ROM port */
    val soundRom = Flipped(AsyncReadWriteMemIO(Config.SOUND_ROM_ADDR_WIDTH, Config.SOUND_ROM_DATA_WIDTH))
    /** Tile ROM port */
    val tileRom = Flipped(BurstReadMemIO(Config.ddrConfig.addrWidth, Config.ddrConfig.dataWidth))
    /** Frame buffer DMA port */
    val fbDMA = Flipped(BurstWriteMemIO(Config.ddrConfig.addrWidth, Config.ddrConfig.dataWidth))
    /** Video DMA port */
    val videoDMA = Flipped(BurstReadMemIO(Config.ddrConfig.addrWidth, Config.ddrConfig.dataWidth))
    /** DDR port */
    val ddr = BurstReadWriteMemIO(Config.ddrConfig.addrWidth, Config.ddrConfig.dataWidth)
    /** SDRAM port */
    val sdram = BurstReadWriteMemIO(Config.sdramConfig.addrWidth, Config.sdramConfig.dataWidth)
  })

  // DDR download cache
  val ddrDownloadCache = Module(new CacheMem(CacheConfig(
    inAddrWidth = DownloadIO.ADDR_WIDTH,
    inDataWidth = DownloadIO.DATA_WIDTH,
    outAddrWidth = Config.ddrConfig.addrWidth,
    outDataWidth = Config.ddrConfig.dataWidth,
    lineWidth = 1,
    depth = 1
  )))
  ddrDownloadCache.io.in <> io.download.asAsyncReadWriteMemIO

  // SDRAM download cache
  val sdramDownloadCache = Module(new CacheMem(CacheConfig(
    inAddrWidth = DownloadIO.ADDR_WIDTH,
    inDataWidth = DownloadIO.DATA_WIDTH,
    outAddrWidth = Config.sdramConfig.addrWidth,
    outDataWidth = Config.sdramConfig.dataWidth,
    lineWidth = Config.sdramConfig.burstLength,
    depth = 1,
    wrapping = true
  )))
  sdramDownloadCache.io.in <> io.download.asAsyncReadWriteMemIO

  // Program ROM cache
  val progRomCache = Module(new CacheMem(CacheConfig(
    inAddrWidth = Config.PROG_ROM_ADDR_WIDTH,
    inDataWidth = Config.PROG_ROM_DATA_WIDTH,
    outAddrWidth = Config.sdramConfig.addrWidth,
    outDataWidth = Config.sdramConfig.dataWidth,
    lineWidth = Config.sdramConfig.burstLength,
    depth = 512,
    offset = Config.PROG_ROM_OFFSET,
    wrapping = true
  )))
  progRomCache.io.in <> io.progRom

  // Sound ROM cache
  val soundRomCache = Module(new CacheMem(CacheConfig(
    inAddrWidth = Config.SOUND_ROM_ADDR_WIDTH,
    inDataWidth = Config.SOUND_ROM_DATA_WIDTH,
    outAddrWidth = Config.sdramConfig.addrWidth,
    outDataWidth = Config.sdramConfig.dataWidth,
    lineWidth = Config.sdramConfig.burstLength,
    depth = 256,
    offset = Config.SOUND_ROM_OFFSET,
    wrapping = true
  )))
  soundRomCache.io.in <> io.soundRom

  // DDR arbiter
  val ddrArbiter = Module(new MemArbiter(4, Config.ddrConfig.addrWidth, Config.ddrConfig.dataWidth))
  ddrArbiter.io.in(0) <> ddrDownloadCache.io.out
  ddrArbiter.io.in(1).asBurstReadMemIO <> io.videoDMA // high-priority required to burst data to the video FIFO
  ddrArbiter.io.in(2).asBurstWriteMemIO <> io.fbDMA
  ddrArbiter.io.in(3).asBurstReadMemIO <> io.tileRom
  ddrArbiter.io.out <> io.ddr

  // SDRAM arbiter
  val sdramArbiter = Module(new MemArbiter(3, Config.sdramConfig.addrWidth, Config.sdramConfig.dataWidth))
  sdramArbiter.io.in(0) <> sdramDownloadCache.io.out
  sdramArbiter.io.in(1) <> soundRomCache.io.out
  sdramArbiter.io.in(2) <> progRomCache.io.out
  sdramArbiter.io.out <> io.sdram

  // Wait until both DDR and SDRAM are ready
  io.download.waitReq := ddrDownloadCache.io.in.waitReq || sdramDownloadCache.io.in.waitReq
}
