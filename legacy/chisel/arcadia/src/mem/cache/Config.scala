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

import arcadia.mem.LineConfig
import chisel3.util.log2Ceil

/**
 * Represents a cache configuration.
 *
 * @param inAddrWidth  The width of the input address bus.
 * @param inDataWidth  The width of the input data bus.
 * @param outAddrWidth The width of the output address bus.
 * @param outDataWidth The width of the output data bus.
 * @param lineWidth    The number of words in a cache line.
 * @param depth        The number of entries in the cache.
 * @param wrapping     A boolean indicating whether burst wrapping should be enabled for the
 *                     cache. When a wrapping burst reaches a burst boundary, the address wraps
 *                     back to the previous burst boundary.
 */
case class Config(override val inAddrWidth: Int,
                  override val inDataWidth: Int,
                  override val outAddrWidth: Int,
                  override val outDataWidth: Int,
                  override val lineWidth: Int,
                  depth: Int,
                  wrapping: Boolean = false) extends LineConfig(inAddrWidth, inDataWidth, outAddrWidth, outDataWidth, lineWidth) {
  /** The width of a cache address index */
  val indexWidth = log2Ceil(depth)
  /** The width of a cache address offset */
  val offsetWidth = log2Ceil(lineWidth)
  /** The width of a cache tag */
  val tagWidth = inAddrWidth - indexWidth - offsetWidth
}
