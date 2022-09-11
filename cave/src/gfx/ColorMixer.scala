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

package cave.gfx

import cave._
import chisel3._
import chisel3.util._

/** The color mixer combines the output from different layers to produce the final pixel. */
class ColorMixer extends Module {
  val io = IO(new Bundle {
    /** Game config port */
    val gameConfig = Input(GameConfig())
    /** Sprite palette entry */
    val spritePen = Input(new PaletteEntry)
    /** Layer 0 palette entry */
    val layer0Pen = Input(new PaletteEntry)
    /** Layer 1 palette entry */
    val layer1Pen = Input(new PaletteEntry)
    /** Layer 2 palette entry */
    val layer2Pen = Input(new PaletteEntry)
    /** Palette RAM port */
    val paletteRam = new PaletteRamIO
    /** Pixel data */
    val dout = Output(UInt(Config.PALETTE_RAM_GPU_DATA_WIDTH.W))
  })

  // Background pen
  val backgroundPen = ColorMixer.backgroundPen(io.gameConfig.index)

  // Mux the layers
  val index = ColorMixer.muxLayers(io.spritePen, io.layer0Pen, io.layer1Pen, io.layer2Pen)

  // Calculate the palette RAM address
  val paletteRamAddr = MuxLookup(index, 0.U, Seq(
    ColorMixer.Priority.FILL.U -> ColorMixer.paletteRamAddr(backgroundPen, 0.U, io.gameConfig.granularity),
    ColorMixer.Priority.SPRITE.U -> ColorMixer.paletteRamAddr(io.spritePen, 0.U, io.gameConfig.granularity),
    ColorMixer.Priority.LAYER0.U -> ColorMixer.paletteRamAddr(io.layer0Pen, io.gameConfig.layer(0).paletteBank, io.gameConfig.granularity),
    ColorMixer.Priority.LAYER1.U -> ColorMixer.paletteRamAddr(io.layer1Pen, io.gameConfig.layer(1).paletteBank, io.gameConfig.granularity),
    ColorMixer.Priority.LAYER2.U -> ColorMixer.paletteRamAddr(io.layer2Pen, io.gameConfig.layer(2).paletteBank, io.gameConfig.granularity)
  ))

  // Outputs
  io.paletteRam.rd := true.B // read-only
  io.paletteRam.addr := paletteRamAddr
  io.dout := RegNext(io.paletteRam.dout)
}

object ColorMixer {
  /** Color mixer priority */
  object Priority {
    val FILL = 0
    val SPRITE = 1
    val LAYER0 = 2
    val LAYER1 = 4
    val LAYER2 = 8
  }

  /**
   * Returns the palette entry used for the fill layer.
   *
   * @param index The game index.
   * @return A palette entry.
   */
  private def backgroundPen(index: UInt): PaletteEntry =
    MuxLookup(index, PaletteEntry(0.U, 0x7f.U, 0.U), Seq(
      Game.DFEVERON.U -> PaletteEntry(0.U, 0x3f.U, 0.U)
    ))

  /**
   * Calculates the palette RAM address from the given palette entry.
   *
   * The address is calculated differently, depending on the number of colors per palette.
   *
   * @param pen         The palette entry.
   * @param bank        The palette RAM bank.
   * @param granularity The number of colors per palette.
   * @return A memory address.
   */
  private def paletteRamAddr(pen: PaletteEntry, bank: UInt, granularity: UInt): UInt =
    MuxLookup(granularity, bank ## pen.palette ## pen.color, Seq(
      16.U -> bank ## pen.palette ## pen.color(3, 0),
      64.U -> bank ## pen.palette ## pen.color(5, 0)
    ))

  /**
   * Calculates the layer with the highest priority.
   *
   * @param spritePen The sprite palette entry.
   * @param layer0Pen The layer 0 palette entry.
   * @param layer1Pen The layer 1 palette entry.
   * @param layer2Pen The layer 2 palette entry.
   * @return The index of the layer with the highest priority.
   */
  private def muxLayers(spritePen: PaletteEntry, layer0Pen: PaletteEntry, layer1Pen: PaletteEntry, layer2Pen: PaletteEntry): UInt = {
    val sprite = List(
      (spritePen.color =/= 0.U && spritePen.priority === 0.U) -> Priority.SPRITE.U,
      (spritePen.color =/= 0.U && spritePen.priority === 1.U) -> Priority.SPRITE.U,
      (spritePen.color =/= 0.U && spritePen.priority === 2.U) -> Priority.SPRITE.U,
      (spritePen.color =/= 0.U && spritePen.priority === 3.U) -> Priority.SPRITE.U
    )

    val layer0 = List(
      (layer0Pen.color =/= 0.U && layer0Pen.priority === 0.U) -> Priority.LAYER0.U,
      (layer0Pen.color =/= 0.U && layer0Pen.priority === 1.U) -> Priority.LAYER0.U,
      (layer0Pen.color =/= 0.U && layer0Pen.priority === 2.U) -> Priority.LAYER0.U,
      (layer0Pen.color =/= 0.U && layer0Pen.priority === 3.U) -> Priority.LAYER0.U
    )

    val layer1 = List(
      (layer1Pen.color =/= 0.U && layer1Pen.priority === 0.U) -> Priority.LAYER1.U,
      (layer1Pen.color =/= 0.U && layer1Pen.priority === 1.U) -> Priority.LAYER1.U,
      (layer1Pen.color =/= 0.U && layer1Pen.priority === 2.U) -> Priority.LAYER1.U,
      (layer1Pen.color =/= 0.U && layer1Pen.priority === 3.U) -> Priority.LAYER1.U
    )

    val layer2 = List(
      (layer2Pen.color =/= 0.U && layer2Pen.priority === 0.U) -> Priority.LAYER2.U,
      (layer2Pen.color =/= 0.U && layer2Pen.priority === 1.U) -> Priority.LAYER2.U,
      (layer2Pen.color =/= 0.U && layer2Pen.priority === 2.U) -> Priority.LAYER2.U,
      (layer2Pen.color =/= 0.U && layer2Pen.priority === 3.U) -> Priority.LAYER2.U
    )

    MuxCase(Priority.FILL.U, Seq(
      layer2(3), layer1(3), layer0(3), sprite(3), // priority 3 (highest)
      layer2(2), layer1(2), layer0(2), sprite(2), // priority 2
      layer2(1), layer1(1), layer0(1), sprite(1), // priority 1
      layer2(0), layer1(0), layer0(0), sprite(0), // priority 0 (lowest)
    ))
  }
}
