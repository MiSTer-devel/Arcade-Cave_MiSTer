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

package arcadia.pocket

import arcadia.mem._
import chisel3._
import chisel3.util._

/** A flow control interface used to download data into the core. */
class BridgeIO extends Bundle {
  /** Write enable */
  val wr = Input(Bool())
  /** Address bus */
  val addr = Input(UInt(BridgeIO.ADDR_WIDTH.W))
  /** Output data bus */
  val dout = Input(Bits(BridgeIO.DATA_WIDTH.W))
  /** Pause flag */
  val pause = Input(Bool()) // FIXME: remove from bridge
  /** Done flag */
  val done = Input(Bool()) // FIXME: remove from bridge

  /** Converts the bridge to an asynchronous write-only memory interface. */
  def rom: WriteMemIO = {
    val mem = Wire(WriteMemIO(BridgeIO.ADDR_WIDTH, BridgeIO.DATA_WIDTH))
    mem.wr := wr
    mem.addr := addr
    mem.mask := Fill(mem.maskWidth, 1.U)
    mem.din := dout
    mem
  }
}

object BridgeIO {
  /** The width of the address bus */
  val ADDR_WIDTH = 32
  /** The width of the data bus */
  val DATA_WIDTH = 32

  def apply() = new BridgeIO
}
