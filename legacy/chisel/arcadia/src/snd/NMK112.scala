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

package arcadia.snd

import arcadia.cpu.m68k.CPU
import arcadia.mem.WriteMemIO
import chisel3._
import chisel3.util.log2Ceil

/**
 * The NMK112 is a custom banking controller that extends the address space of the OKIM6295 ADPCM
 * decoder.
 *
 * It divides the address space of the OKIM6295 into four separate 64KB banks, where each bank can
 * be mapped to a different page in the sound ROM. This allows it to simultaneously play four
 * channels, where each channel addresses data from a different bank in the sound ROM.
 *
 * {{{
 * 0x00000-0x000ff: phrase table (bank 0)
 * 0x00100-0x001ff: phrase table (bank 1)
 * 0x00200-0x002ff: phrase table (bank 2)
 * 0x00300-0x003ff: phrase table (bank 3)
 * 0x00400-0x0ffff: ADPCM data (bank 0)
 * 0x10400-0x1ffff: ADPCM data (bank 1)
 * 0x20400-0x2ffff: ADPCM data (bank 2)
 * 0x30400-0x3ffff: ADPCM data (bank 3)
 * }}}
 */
class NMK112 extends Module {
  val io = IO(new Bundle {
    /** CPU port */
    val cpu = Flipped(WriteMemIO(CPU.ADDR_WIDTH, CPU.DATA_WIDTH))
    /** Address port */
    val addr = Vec(NMK112.CHIP_COUNT, new Bundle {
      val in = Input(UInt(NMK112.SOUND_ROM_ADDR_WIDTH.W))
      val out = Output(UInt(NMK112.SOUND_ROM_ADDR_WIDTH.W))
    })
    /** Phrase table mask */
    val mask = Input(UInt(NMK112.CHIP_COUNT.W))
  })

  /**
   * Transforms a sound ROM memory address for the given chip, using the current bank settings.
   *
   * @param chip The chip index.
   * @param addr The memory address.
   * @return A memory address.
   */
  def transform(chip: Int)(addr: UInt): UInt = {
    io.addr(chip).in := addr
    io.addr(chip).out
  }

  // Page table register
  val pageTableReg = Reg(Vec(NMK112.CHIP_COUNT, Vec(NMK112.BANK_COUNT, UInt(log2Ceil(NMK112.PAGE_COUNT).W))))

  // Set page for the current chip and bank
  when(io.cpu.wr) {
    val chip = io.cpu.addr(2)
    val bank = io.cpu.addr(1, 0)
    pageTableReg(chip)(bank) := io.cpu.din
  }

  // Map addresses for each chip
  0.until(NMK112.CHIP_COUNT).foreach { i =>
    io.addr(i).out := NMK112.mapAddress(pageTableReg(i), io.addr(i).in, io.mask(i))
  }

  // Debug
  if (sys.env.get("DEBUG").contains("1")) {
    printf(p"NMK112(table: $pageTableReg)\n")
  }
}

object NMK112 {
  /** The width of the sound ROM address bus */
  val SOUND_ROM_ADDR_WIDTH = 25
  /** The number of chips */
  val CHIP_COUNT = 2
  /** The number of banks */
  val BANK_COUNT = 4
  /** The maximum number of pages */
  val PAGE_COUNT = 32

  /**
   * Maps a memory address using the given page table.
   *
   * If the phrase table mask bit is set, then the phrase table will not be bank switched.
   * Otherwise, both the phrase table and ADPCM data will be bank switched.
   *
   * @param pageTable The page table.
   * @param addr      The memory address.
   * @param mask      The phrase table mask bit.
   * @return A memory address.
   */
  private def mapAddress(pageTable: Vec[UInt], addr: UInt, mask: Bool) = {
    val bank = Mux(mask || addr > 0x400.U, addr(17, 16), addr(9, 8))
    pageTable(bank) ## addr(15, 0)
  }
}
