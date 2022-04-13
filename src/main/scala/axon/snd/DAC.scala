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

package axon.snd

import chisel3._
import chisel3.util._

/** A sigma-delta digital to analogue converter (DAC) */
class DAC(width: Int = 16) extends Module {
  val io = IO(new Bundle {
    /** Sample value */
    val sample = Input(SInt(width.W))
    /** Asserted when the signal is valid */
    val valid = Input(Bool())
    /** Output value */
    val q = Output(Bits(1.W))
  })

  // Registers
  val accumulatorReg = Reg(UInt((width + 1).W))
  val sampleReg = RegEnable(io.sample, 0.S, io.valid)

  // Flip sample bits
  val sample = ~sampleReg(15) ## sampleReg(14, 0)

  // Add the sample to the accumulator
  accumulatorReg := accumulatorReg(width - 1, 0) +& sample

  // Output
  io.q := accumulatorReg(width)
}
