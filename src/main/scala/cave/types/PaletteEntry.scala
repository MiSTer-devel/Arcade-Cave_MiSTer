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

import cave.Config
import chisel3._

/** Represent an entry in a color palette. */
class PaletteEntry extends Bundle {
  /** Palette index */
  val palette = UInt(Config.PALETTE_ENTRY_PALLETE_WIDTH.W)
  /** Color index */
  val color = UInt(Config.PALETTE_ENTRY_COLOR_WIDTH.W)

  /**
   * Asserted when the palette entry is transparent.
   *
   * @note The CAVE first-generation hardware handles transparency the following way:
   *
   * If the color code is zero the pixel is transparent. This results in 15 usable colors for a
   * tile. Even if the color at the first index of the palette is not zero, the pixel is still
   * transparent.
   *
   * It is difficult to understand why CAVE didn't use the MSB bit of the 16 bit color word to
   * indicate transparency. This would allow for 16 colors out of 2^15 colors for each tile instead
   * of 15 colors of 2^15. With the CAVE CV1000 (SH3) hardware, they use the MSB bit of the 16-bit
   * word as a transparency bit, while the colors remain RGB555.
   *
   * One wonders why they didn't do this on first-generation hardware.
   */
  def isTransparent: Bool = color === 0.U
}

object PaletteEntry {
  /**
   * Constructs a new palette entry.
   *
   * @param palette The palette index.
   * @param color The color index.
   */
  def apply(palette: UInt, color: UInt): PaletteEntry = {
    val wire = Wire(new PaletteEntry)
    wire.palette := palette
    wire.color := color
    wire
  }
}
