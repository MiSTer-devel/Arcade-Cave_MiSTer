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

/**
 * Represents a line configuration.
 *
 * @param inAddrWidth  The width of the input address bus.
 * @param inDataWidth  The width of the input data bus.
 * @param outAddrWidth The width of the output address bus.
 * @param outDataWidth The width of the output data bus.
 * @param lineWidth    The number of words in a line.
 */
abstract class LineConfig(val inAddrWidth: Int,
                          val inDataWidth: Int,
                          val outAddrWidth: Int,
                          val outDataWidth: Int,
                          val lineWidth: Int) {
  /** The number of input words in a line */
  val inWords = outDataWidth * lineWidth / inDataWidth
  /** The number of bytes in an input word */
  val inBytes = inDataWidth / 8
  /** The number of bytes in an output word */
  val outBytes = outDataWidth / 8
}

/**
 * A line is a set of words that can be viewed as two different groupings: in words and out words.
 *
 * @param config The line configuration.
 */
class Line(private val config: LineConfig) extends Bundle {
  /** Raw line data */
  val words: Vec[Bits] = Vec(config.lineWidth, Bits(config.outDataWidth.W))

  /**
   * Returns the line represented as a vector input words.
   *
   * @return A vector of words.
   */
  def inWords: Vec[Bits] = {
    val ws = Util.decode(words.asUInt, config.inWords, config.inDataWidth)
    VecInit(ws)
  }

  /**
   * Returns the line represented as a vector output words.
   *
   * @return A vector of words.
   */
  def outWords: Vec[Bits] = words
}
