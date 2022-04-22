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

package axon.types

import chisel3._

/** An interface that contains the user options. */
class OptionsIO extends Bundle {
  /** Asserted when SDRAM is available */
  val sdram = Input(Bool())
  /** CRT offset */
  val offset = Input(SVec2(OptionsIO.SCREEN_OFFSET_WIDTH.W))
  /** Asserted when screen rotation is enabled */
  val rotate = Input(Bool())
  /** Asserted when screen flipping is enabled */
  val flip = Input(Bool())
  /** Asserted when video compatibility (60Hz) mode is enabled */
  val compatibility = Input(Bool())
  /** Layer enable flags */
  val layerEnable = Input(new Bundle {
    val sprites = Bool()
    val layer0 = Bool()
    val layer1 = Bool()
    val layer2 = Bool()
  })
  /** Asserted when row scroll is enabled */
  val rowScrollEnable = Input(Bool())
  /** Asserted when row select is enabled */
  val rowSelectEnable = Input(Bool())
  /** Game index */
  val gameIndex = Input(UInt(OptionsIO.GAME_INDEX_WIDTH.W))
}

object OptionsIO {
  /** The width of the screen offset value */
  val SCREEN_OFFSET_WIDTH = 4
  /** The width of the game index */
  val GAME_INDEX_WIDTH = 4

  def apply() = new OptionsIO
}