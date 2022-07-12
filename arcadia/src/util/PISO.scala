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

package arcadia.util

import chisel3._

/**
 * A PISO (parallel-in, serial-out) used to buffer data.
 *
 * @param n         The number of elements.
 * @param t         The input data type.
 * @param threshold The threshold at which the almost empty signal will be asserted.
 */
class PISO[T <: Data](n: Int, t: T, threshold: Int = 1) extends Module {
  val io = IO(new Bundle {
    /** Read enable */
    val rd = Input(Bool())
    /** Write enable */
    val wr = Input(Bool())
    /** Asserted when the PISO is empty */
    val isEmpty = Output(Bool())
    /** Asserted when the PISO is almost empty */
    val isAlmostEmpty = Output(Bool())
    /** Data input port */
    val din = Input(Vec(n, t))
    /** Data output port */
    val dout = Output(t)
  })

  // Registers
  val pisoReg = Reg(Vec(n, t))
  val pisoCounterReg = RegInit(0.U)

  // Control signals
  val pisoEmpty = pisoCounterReg === 0.U
  val pisoAlmostEmpty = pisoCounterReg === threshold.U

  // Shift data in/out of PISO
  when(io.wr) {
    pisoReg := io.din
    pisoCounterReg := n.U
  }.elsewhen(io.rd && !pisoEmpty) {
    pisoReg := pisoReg.tail :+ pisoReg.head
    pisoCounterReg := pisoCounterReg - 1.U
  }

  // Outputs
  io.isEmpty := pisoEmpty
  io.isAlmostEmpty := pisoAlmostEmpty
  io.dout := pisoReg.head
}
