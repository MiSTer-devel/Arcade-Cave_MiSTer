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

package arcadia.mem.sdram

import chisel3._
import chisel3.util.log2Ceil

/** Represents the address of a word stored in SDRAM. */
class Address(private val config: Config) extends Bundle {
  /** The bank address */
  val bank = UInt(config.bankWidth.W)
  /** The row address */
  val row = UInt(config.rowWidth.W)
  /** The column address */
  val col = UInt(config.colWidth.W)
}

object Address {
  /**
   * Converts a byte address to a SDRAM address.
   *
   * @param config The SDRAM configuration.
   * @param addr   The memory address.
   */
  def fromByteAddress(config: Config, addr: UInt) = {
    val n = log2Ceil(config.dataWidth / 8)
    (addr >> n).asTypeOf(new Address(config))
  }
}