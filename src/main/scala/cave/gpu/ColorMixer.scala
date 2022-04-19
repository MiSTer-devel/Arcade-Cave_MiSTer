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

package cave.gpu

import axon.types._
import cave.Config
import cave.types._
import chisel3._
import chisel3.util._

/** The color mixer combines the output from different layers to produce the final pixel. */
class ColorMixer extends Module {
  val io = IO(new Bundle {
    /** The maximum number of colors per palette */
    val numColors = Input(UInt(9.W))
    /** Layer 0 palette entry */
    val layer0Pen = Input(new PaletteEntry)
    /** Layer 1 palette entry */
    val layer1Pen = Input(new PaletteEntry)
    /** Palette RAM port */
    val paletteRam = new PaletteRamIO
    /** RGB output */
    val rgb = Output(new RGB(Config.DDR_FRAME_BUFFER_BITS_PER_CHANNEL))
  })

  // Find the layer with the highest priority
  val layer = ColorMixer.muxLayers(io.layer0Pen, io.layer1Pen)

  // Mux the layers
  val paletteRamAddr = MuxLookup(layer, 0.U, Seq(
    ColorMixer.LAYER0.U -> io.layer0Pen.toAddr(io.numColors),
    ColorMixer.LAYER1.U -> io.layer1Pen.toAddr(io.numColors)
  ))

  // Outputs
  io.paletteRam.rd := true.B // read-only
  io.paletteRam.addr := paletteRamAddr
  io.rgb := RegNext(ColorMixer.decodeRGB(io.paletteRam.dout))
}

object ColorMixer {
  val LAYER0 = 0
  val LAYER1 = 1
  val LAYER2 = 2
  val SPRITE = 3
  val FILL = 4

  /**
   * Calculates the layer with the highest priority for the given layer data.
   *
   * @param layer0Pen The layer 0 palette entry.
   * @param layer1Pen The layer 1 palette entry.
   */
  private def muxLayers(layer0Pen: PaletteEntry, layer1Pen: PaletteEntry): UInt =
    MuxCase(FILL.U, Seq(
      !layer1Pen.isTransparent -> LAYER1.U,
      !layer0Pen.isTransparent -> LAYER0.U
    ))

  /**
   * Decodes a RGB color from a 16-bit word.
   *
   * @param data The color data.
   */
  private def decodeRGB(data: UInt): RGB = {
    val b = data(4, 0) ## data(4, 2)
    val r = data(9, 5) ## data(9, 7)
    val g = data(14, 10) ## data(14, 12)
    RGB(r, g, b)
  }
}
