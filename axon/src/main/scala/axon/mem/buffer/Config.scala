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

package axon.mem.buffer

/**
 * Represents a buffer configuration.
 *
 * @param inAddrWidth    The width of the input address bus.
 * @param inDataWidth    The width of the input data bus.
 * @param outAddrWidth   The width of the output address bus.
 * @param outDataWidth   The width of the output data bus.
 * @param burstLength    The number of words to transfer during a burst.
 * @param bigEndian A boolean indicating whether the endianness of the input words should be
 *                       swapped.
 */
case class Config(inAddrWidth: Int,
                  inDataWidth: Int,
                  outAddrWidth: Int,
                  outDataWidth: Int,
                  burstLength: Int,
                  bigEndian: Boolean = false) {
  /** The number of input words in a cache line */
  val inWords = outDataWidth * burstLength / inDataWidth
  /** The number of bytes in an input word */
  val inBytes = inDataWidth / 8
  /** The number of bytes in an output word */
  val outBytes = outDataWidth / 8
}
