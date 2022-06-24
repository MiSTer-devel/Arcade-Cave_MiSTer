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

package arcadia.mem

import chisel3._

/**
 * This module wraps an external single-port ROM module.
 *
 * @param addrWidth The width of the address bus.
 * @param dataWidth The width of the data bus.
 */
class SinglePortRom(addrWidth: Int, dataWidth: Int, depth: Int, initFile: String) extends Module {
  val io = IO(Flipped(ReadMemIO(addrWidth, dataWidth)))

  class WrappedSinglePortRam extends BlackBox(
    Map(
      "ADDR_WIDTH" -> addrWidth,
      "DATA_WIDTH" -> dataWidth,
      "DEPTH" -> depth,
      "INIT_FILE" -> initFile
    )
  ) {
    val io = IO(new Bundle {
      val clk = Input(Clock())
      val rd = Input(Bool())
      val addr = Input(UInt(addrWidth.W))
      val dout = Output(Bits(dataWidth.W))
    })

    override def desiredName = "single_port_rom"
  }

  val rom = Module(new WrappedSinglePortRam)
  rom.io.clk := clock
  rom.io.rd := io.rd
  rom.io.addr := io.addr
  io.dout := rom.io.dout
}
