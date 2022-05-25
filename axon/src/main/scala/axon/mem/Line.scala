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

package axon.mem

import axon.Util
import chisel3._
import chisel3.util._

/**
 * A cache line is stored internally as a vector of words that has the same width as the output data
 * bus.
 *
 * A cache line can also be represented a vector of input words, by rearranging the byte grouping.
 *
 * @param inDataWidth    The width of the input data bus.
 * @param outDataWidth   The width of the output data bus.
 * @param lineWidth      The number of words in a cache line.
 * @param swapEndianness A boolean indicating whether the endianness of the input words should be
 *                       swapped.
 */
class Line(private val inDataWidth: Int,
           private val outDataWidth: Int,
           private val lineWidth: Int,
           private val swapEndianness: Boolean) extends Bundle {
  private val inByteWidth = inDataWidth / 8
  private val numInWords = outDataWidth * lineWidth / inDataWidth

  /** The cache line data */
  val words: Vec[Bits] = Vec(lineWidth, Bits(outDataWidth.W))

  /** Returns the cache line represented as a vector input words */
  def inWords: Vec[Bits] = {
    val ws: Seq[Bits] = Util.decode(words.asUInt, numInWords * inByteWidth, 8)
      .grouped(inByteWidth)
      .map { b => if (swapEndianness) Cat(b.reverse) else Cat(b) }
      .toSeq
    VecInit(ws)
  }

  /** The output words in the cache line */
  def outWords: Vec[Bits] = words
}
