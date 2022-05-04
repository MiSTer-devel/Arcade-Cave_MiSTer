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

package axon.mem.sdram

import chisel3._

/**
 * An interface for controlling a SDRAM chip.
 *
 * @param config The SDRAM configuration.
 */
class SDRAMIO(config: Config) extends Bundle {
  /** Clock enable */
  val cke = Output(Bool())
  /** Chip select (active-low) */
  val cs_n = Output(Bool())
  /** Row address strobe (active-low) */
  val ras_n = Output(Bool())
  /** Column address strobe (active-low) */
  val cas_n = Output(Bool())
  /** Write enable (active-low) */
  val we_n = Output(Bool())
  /** Output enable */
  val oe = Output(Bool())
  /** Bank bus */
  val bank = Output(UInt(config.bankWidth.W))
  /** Address bus */
  val addr = Output(UInt(config.rowWidth.W))
  /** Data input bus */
  val din = Output(Bits(config.dataWidth.W))
  /** Data output bus */
  val dout = Input(Bits(config.dataWidth.W))
}

object SDRAMIO {
  def apply(config: Config) = new SDRAMIO(config)
}