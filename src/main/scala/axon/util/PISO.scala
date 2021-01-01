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

package axon.util

import chisel3._

/**
 * A PISO (parallel-in, serial-out) used to buffer data.
 *
 * @param n              The number of elements.
 * @param width          The data width.
 * @param emptyThreshold The threshold at which the [[isAlmostEmpty]] flag will be asserted.
 */
class PISO(n: Int, width: Int, emptyThreshold: Int = 1) extends Module {
  val io = IO(new Bundle {
    val wr = Input(Bool())
    val isEmpty = Output(Bool())
    val isAlmostEmpty = Output(Bool())
    val din = Input(Vec(n, Bits(width.W)))
    val dout = Output(Bits(width.W))
  })

  val pisoReg = Reg(Vec(n, Bits(width.W)))
  val pisoCounterReg = RegInit(0.U)

  val pisoEmpty = pisoCounterReg === 0.U
  val pisoAlmostEmpty = pisoCounterReg === emptyThreshold.U

  // Shift data in/out of PISO
  when(io.wr) {
    pisoReg := io.din
    pisoCounterReg := n.U
  }.elsewhen(!pisoEmpty) {
    pisoReg := pisoReg.tail :+ pisoReg.head
    pisoCounterReg := pisoCounterReg - 1.U
  }

  // Outputs
  io.isEmpty := pisoEmpty
  io.isAlmostEmpty := pisoAlmostEmpty
  io.dout := pisoReg.head
}
