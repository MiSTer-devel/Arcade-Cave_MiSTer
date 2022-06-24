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

package arcadia.mem.cache

import chisel3._
import chisel3.util.log2Ceil

/**
 * Represents the location of a word stored in the cache.
 *
 * @param config The cache configuration.
 */
class Address(private val config: Config) extends Bundle {
  /** The most significant bits of the address */
  val tag = UInt(config.tagWidth.W)
  /** The index of the cache entry within the cache */
  val index = UInt(config.indexWidth.W)
  /** The offset of the word within the cache line */
  val offset = UInt(config.offsetWidth.W)
}

object Address {
  /**
   * Creates a cache address.
   *
   * @param config The cache configuration.
   * @param tag    The tag value.
   * @param index  The index value.
   * @param offset The offset value.
   * @return A cache address.
   */
  def apply(config: Config, tag: UInt, index: UInt, offset: UInt): Address = {
    val wire = Wire(new Address(config))
    wire.tag := tag
    wire.index := index
    wire.offset := offset
    wire
  }

  /**
   * Creates a cache address from a byte address.
   *
   * @param config The cache configuration.
   * @param addr   The byte address.
   * @return A cache address.
   */
  def apply(config: Config, addr: UInt): Address =
    (addr >> log2Ceil(config.outBytes)).asTypeOf(new Address(config))
}
