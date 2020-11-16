/*
 *    __   __     __  __     __         __
 *   /\ "-.\ \   /\ \/\ \   /\ \       /\ \
 *   \ \ \-.  \  \ \ \_\ \  \ \ \____  \ \ \____
 *    \ \_\\"\_\  \ \_____\  \ \_____\  \ \_____\
 *     \/_/ \/_/   \/_____/   \/_____/   \/_____/
 *    ______     ______       __     ______     ______     ______
 *   /\  __ \   /\  == \     /\ \   /\  ___\   /\  ___\   /\__  _\
 *   \ \ \/\ \  \ \  __<    _\_\ \  \ \  __\   \ \ \____  \/_/\ \/
 *    \ \_____\  \ \_____\ /\_____\  \ \_____\  \ \_____\    \ \_\
 *     \/_____/   \/_____/ \/_____/   \/_____/   \/_____/     \/_/
 *
 *  https://joshbassett.info
 *  https://twitter.com/nullobject
 *  https://github.com/nullobject
 *
 *  Copyright (c) 2020 Josh Bassett
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
 */

package cave.types

import axon.Util
import chisel3._

/** Represents a tile descriptor. */
class Tile extends Bundle {
  /** Priority */
  val priority = UInt(2.W)
  /** Color code */
  val colorCode = UInt(6.W)
  /** Tile code */
  val code = UInt(24.W)
}

object Tile {
  /**
   * Decodes a tile from the given data.
   *
   * {{{
   * word   bits                  description
   * -----+-fedc-ba98-7654-3210-+----------------
   *    0 | xx-- ---- ---- ---  | priority
   *      | --xx xxxx ---- ---- | color
   *      | ---- ---- xxxx xxxx | code hi
   *    1 | xxxx xxxx xxxx xxxx | code lo
   * }}}
   *
   * @param data The tile data.
   */
  def decode(data: Bits): Tile = {
    val words = Util.decode(data, 2, 16)
    val tile = Wire(new Tile)
    tile.priority := words(0)(15, 14)
    tile.colorCode := words(0)(13, 8)
    tile.code := words(0)(7, 0) ## words(1)(15, 0)
    tile
  }
}

