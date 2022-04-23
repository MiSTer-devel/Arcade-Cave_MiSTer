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

import axon.mem._
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
    val ddr = DDRIO(Config.ddrConfig)
    /** SDRAM port */
    val sdram = BurstReadWriteMemIO(Config.sdramConfig.addrWidth, Config.sdramConfig.dataWidth)
    /** Program ROM port */
    val progRom = Flipped(new ProgRomIO)
    /** Sound ROM port */
    val soundRom = Flipped(new SoundRomIO)
    /** EEPROM port */
    val eeprom = Flipped(new EEPROMIO)
    /** Layer tile ROM port */
    val layerTileRom = Flipped(Vec(Config.LAYER_COUNT, new LayerRomIO))
    /** Sprite tile ROM port */
    val spriteTileRom = Flipped(new SpriteRomIO)
    /** Frame buffer port */
    val frameBuffer = Flipped(BurstWriteMemIO(Config.ddrConfig.addrWidth, Config.ddrConfig.dataWidth))
  })

  // The DDR download cache is used to buffer IOCTL data, so that complete words can be written to
  // memory.
  val ddrDownloadCache = Module(new Cache(CacheConfig(
    inAddrWidth = IOCTL.ADDR_WIDTH,
    inDataWidth = IOCTL.DATA_WIDTH,
    outAddrWidth = Config.ddrConfig.addrWidth,
    outDataWidth = Config.ddrConfig.dataWidth,
    lineWidth = 1,
    depth = 1
  )))
  ddrDownloadCache.io.offset := Config.DDR_DOWNLOAD_OFFSET.U
  ddrDownloadCache.io.in <> io.ioctl.asAsyncReadWriteMemIO(IOCTL.ROM_INDEX)

  // The SDRAM download cache is used to buffer IOCTL data, so that complete words can be written
  // to memory.
  val sdramDownloadCache = Module(new Cache(CacheConfig(
    inAddrWidth = IOCTL.ADDR_WIDTH,
    inDataWidth = IOCTL.DATA_WIDTH,
    outAddrWidth = Config.sdramConfig.addrWidth,
    outDataWidth = Config.sdramConfig.dataWidth,
    lineWidth = Config.sdramConfig.burstLength,
    depth = 1,
    wrapping = true
  )))
  sdramDownloadCache.io.offset := 0.U
  sdramDownloadCache.io.in <> io.ioctl.asAsyncReadWriteMemIO(IOCTL.ROM_INDEX)

  // Program ROM cache
  val progRomCache = Module(new Cache(CacheConfig(
    inAddrWidth = Config.PROG_ROM_ADDR_WIDTH,
    inDataWidth = Config.PROG_ROM_DATA_WIDTH,
    outAddrWidth = Config.ddrConfig.addrWidth,
    outDataWidth = Config.ddrConfig.dataWidth,
    lineWidth = 4,
    depth = 256
  )))
  progRomCache.io.offset := io.gameConfig.progRomOffset + Config.DDR_DOWNLOAD_OFFSET.U
  progRomCache.io.in.asAsyncReadMemIO <> io.progRom

  // Sound ROM cache
  val soundRomCache = Module(new Cache(CacheConfig(
    inAddrWidth = Config.SOUND_ROM_ADDR_WIDTH,
    inDataWidth = Config.SOUND_ROM_DATA_WIDTH,
    outAddrWidth = Config.ddrConfig.addrWidth,
    outDataWidth = Config.ddrConfig.dataWidth,
    lineWidth = 4,
    depth = 256,
    wrapping = true
  )))
  soundRomCache.io.in.asAsyncReadMemIO <> io.soundRom
  soundRomCache.io.offset := io.gameConfig.soundRomOffset + Config.DDR_DOWNLOAD_OFFSET.U

  // EEPROM cache
  val eepromCache = Module(new Cache(CacheConfig(
    inAddrWidth = Config.EEPROM_ADDR_WIDTH,
    inDataWidth = Config.EEPROM_DATA_WIDTH,
    outAddrWidth = Config.ddrConfig.addrWidth,
    outDataWidth = Config.ddrConfig.dataWidth,
    lineWidth = 4,
    depth = 4
  )))
  eepromCache.io.in <> io.eeprom
  eepromCache.io.offset := io.gameConfig.eepromOffset + Config.DDR_DOWNLOAD_OFFSET.U

  // Layer tile ROM cache
  val layerRomCache = 0.until(Config.LAYER_COUNT).map { i =>
    val cache = Module(new Cache(CacheConfig(
      inAddrWidth = Config.TILE_ROM_ADDR_WIDTH,
      inDataWidth = Config.TILE_ROM_DATA_WIDTH,
      outAddrWidth = Config.sdramConfig.addrWidth,
      outDataWidth = Config.sdramConfig.dataWidth,
      lineWidth = Config.sdramConfig.burstLength,
      depth = 256,
      wrapping = true
    )))
    cache.io.in.asAsyncReadMemIO <> io.layerTileRom(i)
    cache.io.offset := io.gameConfig.layer(i).romOffset
    cache
  }

  // DDR arbiter
  val ddrArbiter = Module(new MemArbiter(6, Config.ddrConfig.addrWidth, Config.ddrConfig.dataWidth))
  ddrArbiter.io.in(0) <> ddrDownloadCache.io.out
  ddrArbiter.io.in(1) <> progRomCache.io.out
  ddrArbiter.io.in(2) <> soundRomCache.io.out
  ddrArbiter.io.in(3) <> eepromCache.io.out
  ddrArbiter.io.in(4).asBurstWriteMemIO <> io.frameBuffer
  ddrArbiter.io.in(5).asBurstReadMemIO <> io.spriteTileRom
  ddrArbiter.io.in(5).addr := io.spriteTileRom.addr + io.gameConfig.sprite.romOffset + Config.DDR_DOWNLOAD_OFFSET.U // override tile ROM address
  ddrArbiter.io.out <> io.ddr

  // SDRAM arbiter
  val sdramArbiter = Module(new MemArbiter(4, Config.sdramConfig.addrWidth, Config.sdramConfig.dataWidth))
  sdramArbiter.io.in(0) <> sdramDownloadCache.io.out
  sdramArbiter.io.in(1) <> layerRomCache(0).io.out
  sdramArbiter.io.in(2) <> layerRomCache(1).io.out
  sdramArbiter.io.in(3) <> layerRomCache(2).io.out
  sdramArbiter.io.out <> io.sdram

  // Wait until both DDR and SDRAM are ready
  io.ioctl.waitReq := ddrDownloadCache.io.in.waitReq || sdramDownloadCache.io.in.waitReq
}
