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

package axon.mem.cache

import axon.Util
import chisel3._

/**
 * Represents an entry stored in the cache.
 *
 * @param config The cache configuration.
 */
class Entry(private val config: Config) extends Bundle {
  /** Flag to indicate whether the cache entry is valid */
  val valid = Bool()
  /** Flag to indicate whether the cache entry is dirty */
  val dirty = Bool()
  /** The most significant bits of the address */
  val tag = UInt(config.tagWidth.W)
  /** The cache line */
  val line = new Line(config)

  /**
   * Determines whether the cache entry matches a given memory address.
   *
   * @param addr The memory address.
   */
  def isHit(addr: Address): Bool = valid && this.tag === addr.tag

  /**
   * Determines whether the cache entry should be evicted for a given memory address.
   *
   * @param addr The memory address.
   */
  def isDirty(addr: Address): Bool = dirty && this.tag =/= addr.tag

  /**
   * Returns the input word at the given offset.
   *
   * @param offset The address offset.
   */
  def inWord(offset: UInt): Bits = Util.swapEndianness(line.inWords(offset))

  /**
   * Returns the output word at the given offset.
   *
   * @param offset The address offset.
   */
  def outWord(offset: UInt): Bits = line.outWords(offset)

  /**
   * Fills the cache line with the given data, and marks the line as valid.
   *
   * @param tag    The cache line tag value.
   * @param offset The address offset.
   * @param data   The data to be written.
   * @return A cache entry.
   */
  def fill(tag: UInt, offset: UInt, data: Bits): Entry = {
    val entry = Wire(new Entry(config))
    entry := this
    entry.line.words(offset) := data
    entry.tag := tag
    entry.valid := true.B
    entry
  }

  /**
   * Merges the given data with the cache line, and marks the line as dirty.
   *
   * @param offset The address offset.
   * @param data   The data to be written.
   * @return A cache entry.
   */
  def merge(offset: UInt, data: Bits): Entry = {
    val words = WireInit(line.inWords)
    words(offset) := Util.swapEndianness(data)

    val entry = Wire(new Entry(config))
    entry := this
    entry.line.words := words.asTypeOf(chiselTypeOf(line.words))
    entry.dirty := true.B
    entry
  }
}

object Entry {
  /**
   * Creates an empty cache entry.
   *
   * @param config The cache configuration.
   * @return A cache entry.
   */
  def zero(config: Config): Entry = {
    0.U.asTypeOf(new Entry(config))
  }
}
