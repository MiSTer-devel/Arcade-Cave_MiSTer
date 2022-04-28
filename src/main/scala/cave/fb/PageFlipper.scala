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

package cave.fb

import chisel3._
import chisel3.util._

/**
 * Generates the memory addresses for frame buffer page flipping, with either double or triple
 * buffering.
 *
 * @param baseAddr The base memory address of the frame buffer.
 */
class PageFlipper(baseAddr: Int) extends Module {
  val io = IO(new Bundle {
    /** Enables triple buffering when asserted, otherwise double buffering is enabled. */
    val mode = Input(Bool())
    /** Swap page A */
    val swapA = Input(Bool())
    /** Swap page B */
    val swapB = Input(Bool())
    /** Memory address for page A */
    val addrA = Output(UInt(32.W))
    /** Memory address for page B */
    val addrB = Output(UInt(32.W))
  })

  // Registers
  val aIndexReg = RegInit(0.U(2.W))
  val bIndexReg = RegInit(1.U(2.W))

  // Swap A index
  when(io.swapA) {
    aIndexReg := Mux(io.mode, PageFlipper.nextIndex(aIndexReg, bIndexReg), ~bIndexReg(0))
  }

  // Swap B index
  when(io.swapB) {
    bIndexReg := Mux(io.mode, PageFlipper.nextIndex(bIndexReg, aIndexReg), ~bIndexReg(0))
  }

  // Outputs
  io.addrA := baseAddr.U(31, 21) ## aIndexReg(1, 0) ## 0.U(19.W)
  io.addrB := baseAddr.U(31, 21) ## bIndexReg(1, 0) ## 0.U(19.W)
}

object PageFlipper {
  /**
   * Returns the next page index for the given pair of indices.
   *
   * @param a The first index.
   * @param b The second index.
   * @return The next page index.
   */
  private def nextIndex(a: UInt, b: UInt) =
    MuxCase(1.U, Seq(
      ((a === 0.U && b === 1.U) || (a === 1.U && b === 0.U)) -> 2.U,
      ((a === 1.U && b === 2.U) || (a === 2.U && b === 1.U)) -> 0.U
    ))
}
