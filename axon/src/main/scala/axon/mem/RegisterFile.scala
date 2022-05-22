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

package axon.mem

import chisel3._
import chisel3.util._

/**
 * A set of registers, presented as a synchronous read/write memory device.
 *
 * @param dataWidth The width of the data bus.
 * @param depth     The number of registers in the register file.
 */
class RegisterFile(dataWidth: Int, depth: Int) extends Module {
  /** The width of the address bus. */
  val ADDR_WIDTH = log2Up(depth)

  val io = IO(new Bundle {
    /** Memory port */
    val mem = Flipped(ReadWriteMemIO(ADDR_WIDTH, dataWidth))
    /** Registers port */
    val regs: Vec[Bits] = Output(Vec(depth, Bits(dataWidth.W)))
  })

  // Data registers
  val regs = Reg(Vec(depth, Bits(dataWidth.W)))

  // Alias the current data register
  val data = regs(io.mem.addr)

  // Split data register into a vector of bytes
  val bytes = data.asTypeOf(Vec(io.mem.maskWidth, Bits(8.W)))

  // Write masked bytes to the data register
  0.until(io.mem.maskWidth).foreach { n =>
    when(io.mem.wr && io.mem.mask(n)) { bytes(n) := io.mem.din((n + 1) * 8 - 1, n * 8) }
  }

  // Concatenate the bytes and update the data register
  data := bytes.asUInt

  // Outputs
  io.mem.dout := data
  io.regs := regs
}
