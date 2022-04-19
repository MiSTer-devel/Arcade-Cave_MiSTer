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

  // Outputs
  io.paletteRam.rd := true.B // read-only
  io.paletteRam.addr := io.layer0Pen.toAddr(io.numColors)
  io.rgb := RegNext(GPU.decodeRGB(io.paletteRam.dout))
}
