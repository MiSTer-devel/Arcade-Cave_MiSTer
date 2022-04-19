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

/**
 * The memory subsystem routes memory requests to either DDR or SDRAM.
 *
 * The program and sound ROMs are stored in SDRAM, because they require low-latency memory access.
 * ROM data is also cached to reduce the need to go to memory for every read request.
 *
 * Everything else is stored in DDR memory.
 */
class MemSys extends Module {
  val io = IO(new Bundle {
    /** Game config port */
    val gameConfig = Input(GameConfig())
    /** IOCTL port */
    val ioctl = IOCTL()
    /** Program ROM port */
    val progRom = Flipped(new ProgRomIO)
    /** Sound ROM port */
    val soundRom = Flipped(new SoundRomIO)
    /** EEPROM port */
    val eeprom = Flipped(new EEPROMIO)
    /** Layer 0 tile ROM port */
    val layer0Rom = Flipped(new LayerRomIO)
    /** Layer 1 tile ROM port */
    val layer1Rom = Flipped(new LayerRomIO)
    /** DDR port */
    val ddr = BurstReadWriteMemIO(Config.ddrConfig.addrWidth, Config.ddrConfig.dataWidth)
    /** SDRAM port */
    val sdram = BurstReadWriteMemIO(Config.sdramConfig.addrWidth, Config.sdramConfig.dataWidth)
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

  // Layer 0 tile ROM cache
  val layer0RomCache = Module(new Cache(CacheConfig(
    inAddrWidth = Config.TILE_ROM_ADDR_WIDTH,
    inDataWidth = Config.TILE_ROM_DATA_WIDTH,
    outAddrWidth = Config.sdramConfig.addrWidth,
    outDataWidth = Config.sdramConfig.dataWidth,
    lineWidth = Config.sdramConfig.burstLength,
    depth = 256,
    wrapping = true
  )))
  layer0RomCache.io.in.asAsyncReadMemIO <> io.layer0Rom
  layer0RomCache.io.offset := io.gameConfig.layer0RomOffset

  // Layer 0 tile ROM cache
  val layer1RomCache = Module(new Cache(CacheConfig(
    inAddrWidth = Config.TILE_ROM_ADDR_WIDTH,
    inDataWidth = Config.TILE_ROM_DATA_WIDTH,
    outAddrWidth = Config.sdramConfig.addrWidth,
    outDataWidth = Config.sdramConfig.dataWidth,
    lineWidth = Config.sdramConfig.burstLength,
    depth = 256,
    wrapping = true
  )))
  layer1RomCache.io.in.asAsyncReadMemIO <> io.layer1Rom
  layer1RomCache.io.offset := io.gameConfig.layer1RomOffset

  // DDR arbiter
  val ddrArbiter = Module(new MemArbiter(4, Config.ddrConfig.addrWidth, Config.ddrConfig.dataWidth))
  ddrArbiter.io.in(0) <> ddrDownloadCache.io.out
  ddrArbiter.io.in(1) <> progRomCache.io.out
  ddrArbiter.io.in(2) <> soundRomCache.io.out
  ddrArbiter.io.in(3) <> eepromCache.io.out
  ddrArbiter.io.out <> io.ddr

  // SDRAM arbiter
  val sdramArbiter = Module(new MemArbiter(3, Config.sdramConfig.addrWidth, Config.sdramConfig.dataWidth))
  sdramArbiter.io.in(0) <> sdramDownloadCache.io.out
  sdramArbiter.io.in(1) <> layer0RomCache.io.out
  sdramArbiter.io.in(2) <> layer1RomCache.io.out
  sdramArbiter.io.out <> io.sdram

  // Wait until both DDR and SDRAM are ready
  io.ioctl.waitReq := ddrDownloadCache.io.in.waitReq || sdramDownloadCache.io.in.waitReq
}
