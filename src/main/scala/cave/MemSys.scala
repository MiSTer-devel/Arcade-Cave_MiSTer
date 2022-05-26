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

import axon.Util
import axon.mem._
import axon.mem.arbiter.BurstMemArbiter
import axon.mem.cache.CacheMem
import axon.mem.dma.BurstReadDMA
import axon.mister._
import cave.types._
import chisel3._

/** The memory subsystem routes memory requests to either DDR or SDRAM. */
class MemSys extends Module {
  val io = IO(new Bundle {
    /** Game config port */
    val gameConfig = Input(GameConfig())
    /** IOCTL port */
    val ioctl = IOCTL()
    /** DDR port */
    val ddr = BurstReadWriteMemIO(Config.ddrConfig)
    /** SDRAM port */
    val sdram = BurstReadWriteMemIO(Config.sdramConfig)
    /** ROM port */
    val rom = Flipped(new RomIO)
    /** Sprite line buffer port */
    val spriteLineBuffer = Flipped(BurstReadMemIO(Config.ddrConfig))
    /** Sprite frame buffer port */
    val spriteFrameBuffer = Flipped(BurstWriteMemIO(Config.ddrConfig))
    /** System frame buffer port */
    val systemFrameBuffer = Flipped(BurstWriteMemIO(Config.ddrConfig))
    /** Asserted when the memory system is ready */
    val ready = Output(Bool())
  })

  // The DDR download buffer is used to buffer ROM data from the IOCTL, so that complete words are
  // written to memory.
  val ddrDownloadBuffer = Module(new BurstBuffer(
    inAddrWidth = IOCTL.ADDR_WIDTH,
    inDataWidth = IOCTL.DATA_WIDTH,
    outAddrWidth = Config.ddrConfig.addrWidth,
    outDataWidth = Config.ddrConfig.dataWidth,
    burstLength = 1
  ))
  ddrDownloadBuffer.io.in <> io.ioctl.rom

  // The SDRAM download buffer is used to buffer ROM data from the copy DMA, so that complete words
  // are written to memory.
  val sdramDownloadBuffer = Module(new BurstBuffer(
    inAddrWidth = Config.ddrConfig.addrWidth,
    inDataWidth = Config.ddrConfig.dataWidth,
    outAddrWidth = Config.sdramConfig.addrWidth,
    outDataWidth = Config.sdramConfig.dataWidth,
    burstLength = Config.sdramConfig.burstLength
  ))
  sdramDownloadBuffer.io.in <> io.ioctl.rom

  // Copies ROM data from DDR to SDRAM
  val copyDma = Module(new BurstReadDMA(Config.copyDownloadDmaConfig))
  copyDma.io.start := !io.ready && Util.falling(io.ioctl.download)
  copyDma.io.out <> sdramDownloadBuffer.io.in

  // Program ROM cache
  val progRomCache = Module(new CacheMem(cache.Config(
    inAddrWidth = Config.PROG_ROM_ADDR_WIDTH,
    inDataWidth = Config.PROG_ROM_DATA_WIDTH,
    outAddrWidth = Config.sdramConfig.addrWidth,
    outDataWidth = Config.sdramConfig.dataWidth,
    lineWidth = Config.sdramConfig.burstLength,
    depth = 128,
    wrapping = true
  )))
  progRomCache.io.enable := io.ready
  progRomCache.io.in.asAsyncReadMemIO <> io.rom.progRom

  // Sound ROM cache
  val soundRomCache = Module(new CacheMem(cache.Config(
    inAddrWidth = Config.SOUND_ROM_ADDR_WIDTH,
    inDataWidth = Config.SOUND_ROM_DATA_WIDTH,
    outAddrWidth = Config.sdramConfig.addrWidth,
    outDataWidth = Config.sdramConfig.dataWidth,
    lineWidth = Config.sdramConfig.burstLength,
    depth = 32,
    wrapping = true
  )))
  soundRomCache.io.enable := io.ready
  soundRomCache.io.in.asAsyncReadMemIO <> io.rom.soundRom

  // EEPROM cache
  val eepromCache = Module(new CacheMem(cache.Config(
    inAddrWidth = Config.EEPROM_ADDR_WIDTH,
    inDataWidth = Config.EEPROM_DATA_WIDTH,
    outAddrWidth = Config.sdramConfig.addrWidth,
    outDataWidth = Config.sdramConfig.dataWidth,
    lineWidth = Config.sdramConfig.burstLength,
    depth = 2,
    wrapping = true
  )))
  eepromCache.io.enable := io.ready
  eepromCache.io.in <> io.rom.eeprom

  // Layer tile ROM cache
  val layerRomCache = 0.until(Config.LAYER_COUNT).map { i =>
    val c = Module(new CacheMem(cache.Config(
      inAddrWidth = Config.TILE_ROM_ADDR_WIDTH,
      inDataWidth = Config.TILE_ROM_DATA_WIDTH,
      outAddrWidth = Config.sdramConfig.addrWidth,
      outDataWidth = Config.sdramConfig.dataWidth,
      lineWidth = Config.sdramConfig.burstLength,
      depth = 8,
      wrapping = true
    )))
    c.io.enable := io.ready
    c.io.in.asAsyncReadMemIO <> io.rom.layerTileRom(i)
    c
  }

  // DDR arbiter
  val ddrArbiter = Module(new BurstMemArbiter(6, Config.ddrConfig.addrWidth, Config.ddrConfig.dataWidth))
  ddrArbiter.connect(
    ddrDownloadBuffer.io.out.mapAddr(_ + Config.IOCTL_DOWNLOAD_BASE_ADDR.U),
    copyDma.io.in.mapAddr(_ + Config.IOCTL_DOWNLOAD_BASE_ADDR.U),
    io.systemFrameBuffer,
    io.spriteLineBuffer,
    io.spriteFrameBuffer,
    io.rom.spriteTileRom.mapAddr(_ + io.gameConfig.sprite.romOffset + Config.IOCTL_DOWNLOAD_BASE_ADDR.U)
  ) <> io.ddr

  // SDRAM arbiter
  val sdramArbiter = Module(new BurstMemArbiter(7, Config.sdramConfig.addrWidth, Config.sdramConfig.dataWidth))
  sdramArbiter.connect(
    sdramDownloadBuffer.io.out,
    progRomCache.io.out.mapAddr(_ + io.gameConfig.progRomOffset),
    soundRomCache.io.out.mapAddr(_ + io.gameConfig.soundRomOffset),
    eepromCache.io.out.mapAddr(_ + io.gameConfig.eepromOffset),
    layerRomCache(0).io.out.mapAddr(_ + io.gameConfig.layer(0).romOffset),
    layerRomCache(1).io.out.mapAddr(_ + io.gameConfig.layer(1).romOffset),
    layerRomCache(2).io.out.mapAddr(_ + io.gameConfig.layer(2).romOffset)
  ) <> io.sdram

  // Latch ready flag when the copy DMA has finished
  io.ready := Util.latchSync(Util.falling(copyDma.io.busy))
}
