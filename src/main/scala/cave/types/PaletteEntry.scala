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
 * Copyright (c) 2021 Josh Bassett
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

package cave.types

import cave.Config
import chisel3._

/** Represent an entry in a color palette. */
class PaletteEntry extends Bundle {
  /** Palette index */
  val palette = UInt(Config.PALETTE_ENTRY_PALETTE_WIDTH.W)
  /** Color index */
  val color = UInt(Config.PALETTE_ENTRY_COLOR_WIDTH.W)

  /**
   * Asserted when the palette entry is transparent.
   *
   * The CAVE first-generation hardware handles transparency the following way:
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
