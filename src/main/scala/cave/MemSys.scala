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
import axon.mem.cache.Cache
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

  // The ready register is asserted when the memory system is ready.
  val readyReg = RegInit(false.B)

  // The DDR download cache is used to buffer download data, so that complete words can be written
  // to memory.
  val ddrDownloadCache = Module(new Cache(cache.Config(
    inAddrWidth = IOCTL.ADDR_WIDTH,
    inDataWidth = IOCTL.DATA_WIDTH,
    outAddrWidth = Config.ddrConfig.addrWidth,
    outDataWidth = Config.ddrConfig.dataWidth,
    lineWidth = 1,
    depth = 1
  )))
  ddrDownloadCache.io.enable := true.B
  ddrDownloadCache.io.in <> io.ioctl.asAsyncReadWriteMemIO(IOCTL.ROM_INDEX)

  // The SDRAM download cache is used to buffer download data, so that complete words can be written
  // to memory.
  val sdramDownloadCache = Module(new Cache(cache.Config(
    inAddrWidth = Config.ddrConfig.addrWidth,
    inDataWidth = Config.ddrConfig.dataWidth,
    outAddrWidth = Config.sdramConfig.addrWidth,
    outDataWidth = Config.sdramConfig.dataWidth,
    lineWidth = Config.sdramConfig.burstLength,
    depth = 1,
    wrapping = true
  )))
  sdramDownloadCache.io.enable := true.B

  // Program ROM cache
  val progRomCache = Module(new Cache(cache.Config(
    inAddrWidth = Config.PROG_ROM_ADDR_WIDTH,
    inDataWidth = Config.PROG_ROM_DATA_WIDTH,
    outAddrWidth = Config.sdramConfig.addrWidth,
    outDataWidth = Config.sdramConfig.dataWidth,
    lineWidth = Config.sdramConfig.burstLength,
    depth = 256,
    wrapping = true,
    swapEndianness = true
  )))
  progRomCache.io.enable := readyReg
  progRomCache.io.in.asAsyncReadMemIO <> io.rom.progRom

  // Sound ROM cache
  val soundRomCache = Module(new Cache(cache.Config(
    inAddrWidth = Config.SOUND_ROM_ADDR_WIDTH,
    inDataWidth = Config.SOUND_ROM_DATA_WIDTH,
    outAddrWidth = Config.sdramConfig.addrWidth,
    outDataWidth = Config.sdramConfig.dataWidth,
    lineWidth = Config.sdramConfig.burstLength,
    depth = 64,
    wrapping = true
  )))
  soundRomCache.io.enable := readyReg
  soundRomCache.io.in.asAsyncReadMemIO <> io.rom.soundRom

  // EEPROM cache
  val eepromCache = Module(new Cache(cache.Config(
    inAddrWidth = Config.EEPROM_ADDR_WIDTH,
    inDataWidth = Config.EEPROM_DATA_WIDTH,
    outAddrWidth = Config.sdramConfig.addrWidth,
    outDataWidth = Config.sdramConfig.dataWidth,
    lineWidth = Config.sdramConfig.burstLength,
    depth = 4,
    wrapping = true,
    swapEndianness = true
  )))
  eepromCache.io.enable := readyReg
  eepromCache.io.in <> io.rom.eeprom

  // Layer tile ROM cache
  val layerRomCache = 0.until(Config.LAYER_COUNT).map { i =>
    val c = Module(new Cache(cache.Config(
      inAddrWidth = Config.TILE_ROM_ADDR_WIDTH,
      inDataWidth = Config.TILE_ROM_DATA_WIDTH,
      outAddrWidth = Config.sdramConfig.addrWidth,
      outDataWidth = Config.sdramConfig.dataWidth,
      lineWidth = Config.sdramConfig.burstLength,
      depth = 16,
      wrapping = true
    )))
    c.io.enable := readyReg
    c.io.in.asAsyncReadMemIO <> io.rom.layerTileRom(i)
    c
  }

  // Copy download data from DDR to SDRAM
  val copyDownloadDma = Module(new BurstReadDMA(Config.copyDownloadDmaConfig))
  copyDownloadDma.io.start := Util.falling(io.ioctl.download)
  copyDownloadDma.io.out <> sdramDownloadCache.io.in.asAsyncWriteMemIO

  // DDR arbiter
  val ddrArbiter = Module(new MemArbiter(6, Config.ddrConfig.addrWidth, Config.ddrConfig.dataWidth))
  ddrArbiter.connect(
    ddrDownloadCache.io.out.mapAddr(_ + Config.IOCTL_DOWNLOAD_BASE_ADDR.U),
    copyDownloadDma.io.in.mapAddr(_ + Config.IOCTL_DOWNLOAD_BASE_ADDR.U).asBurstReadWriteMemIO,
    io.systemFrameBuffer.asBurstReadWriteMemIO,
    io.spriteLineBuffer.asBurstReadWriteMemIO,
    io.spriteFrameBuffer.asBurstReadWriteMemIO,
    io.rom.spriteTileRom.mapAddr(_ + io.gameConfig.sprite.romOffset + Config.IOCTL_DOWNLOAD_BASE_ADDR.U).asBurstReadWriteMemIO
  ) <> io.ddr

  // SDRAM arbiter
  val sdramArbiter = Module(new MemArbiter(7, Config.sdramConfig.addrWidth, Config.sdramConfig.dataWidth))
  sdramArbiter.connect(
    sdramDownloadCache.io.out,
    progRomCache.io.out.mapAddr(_ + io.gameConfig.progRomOffset),
    soundRomCache.io.out.mapAddr(_ + io.gameConfig.soundRomOffset),
    eepromCache.io.out.mapAddr(_ + io.gameConfig.eepromOffset),
    layerRomCache(0).io.out.mapAddr(_ + io.gameConfig.layer(0).romOffset),
    layerRomCache(1).io.out.mapAddr(_ + io.gameConfig.layer(1).romOffset),
    layerRomCache(2).io.out.mapAddr(_ + io.gameConfig.layer(2).romOffset)
  ) <> io.sdram

  // Toggle ready register
  when(Util.rising(io.ioctl.download)) {
    readyReg := false.B
  }.elsewhen(Util.falling(copyDownloadDma.io.busy)) {
    readyReg := true.B
  }

  // Outputs
  io.ready := readyReg
}
