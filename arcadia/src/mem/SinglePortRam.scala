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
 * This module wraps an external single-port RAM module.
 *
 * @param addrWidth  The width of the address bus.
 * @param dataWidth  The width of the data bus.
 * @param depth      The optional memory depth (in words).
 * @param maskEnable A boolean value indicating whether byte masking is enabled.
 */
class SinglePortRam(addrWidth: Int,
                    dataWidth: Int,
                    depth: Option[Int] = None,
                    maskEnable: Boolean = false) extends Module {
  val io = IO(Flipped(ReadWriteMemIO(addrWidth, dataWidth)))

  private val depth_ = depth.getOrElse(0)

  class WrappedSinglePortRam extends BlackBox(Map(
    "ADDR_WIDTH" -> addrWidth,
    "DATA_WIDTH" -> dataWidth,
    "DEPTH" -> depth_,
    "MASK_ENABLE" -> (if (maskEnable) "TRUE" else "FALSE"),
  )) {
    val io = IO(new Bundle {
      val clk = Input(Clock())
      val rd = Input(Bool())
      val wr = Input(Bool())
      val addr = Input(UInt(addrWidth.W))
      val mask = Input(Bits((dataWidth / 8).W))
      val din = Input(Bits(dataWidth.W))
      val dout = Output(Bits(dataWidth.W))
    })

    override def desiredName = "single_port_ram"
  }

  val ram = Module(new WrappedSinglePortRam)
  ram.io.clk := clock
  ram.io.rd := io.rd
  ram.io.wr := io.wr
  ram.io.addr := io.addr
  ram.io.mask := io.mask
  ram.io.din := io.din
  io.dout := ram.io.dout
}
