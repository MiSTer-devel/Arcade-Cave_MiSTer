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

import chisel3._

/** Represents an entry stored in the cache. */
class Entry(private val config: Config) extends Bundle {
  /** The cache line */
  val line = new Line(config)
  /** The most significant bits of the address */
  val tag = UInt(config.tagWidth.W)
  /** Flag to indicate whether the cache entry is valid */
  val valid = Bool()
  /** Flag to indicate whether the cache entry is dirty */
  val dirty = Bool()

  /**
   * Returns the input word at the given offset.
   *
   * @param offset The address offset.
   */
  def inWord(offset: UInt): Bits = line.inWords(offset)

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
   */
  def fill(tag: UInt, offset: UInt, data: Bits): Unit = {
    line.words(offset) := data
    this.tag := tag
    valid := true.B
  }

  /**
   * Merges the given data with the cache line, and marks the line as dirty.
   *
   * @param offset The address offset.
   * @param data   The data to be written.
   */
  def merge(offset: UInt, data: Bits): Unit = {
    val words = WireInit(line.inWords)
    words(offset) := data
    line.words := words.asTypeOf(chiselTypeOf(line.words))
    dirty := true.B
  }
}
