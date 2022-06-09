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
    /** Enable triple buffering when asserted, otherwise double buffering is used. */
    val mode = Input(Bool())
    /** Swap read page */
    val swapRead = Input(Bool())
    /** Swap write page */
    val swapWrite = Input(Bool())
    /** Memory address for read page */
    val addrRead = Output(UInt(32.W))
    /** Memory address for write page */
    val addrWrite = Output(UInt(32.W))
  })

  // Registers
  val rdIndexReg = RegInit(0.U(2.W))
  val wrIndexReg = RegInit(1.U(2.W))

  when(io.mode) {
    when(io.swapRead && io.swapWrite) {
      rdIndexReg := wrIndexReg
      wrIndexReg := PageFlipper.nextIndex(wrIndexReg, rdIndexReg)
    }.elsewhen(io.swapRead) {
      rdIndexReg := PageFlipper.nextIndex(rdIndexReg, wrIndexReg)
    }.elsewhen(io.swapWrite) {
      wrIndexReg := PageFlipper.nextIndex(wrIndexReg, rdIndexReg)
    }
  }.otherwise {
    // In double buffer mode, we only swap the write page (the read page is always opposite).
    when(io.swapWrite) {
      rdIndexReg := wrIndexReg(0)
      wrIndexReg := ~wrIndexReg(0)
    }
  }

  // Outputs
  io.addrRead := baseAddr.U(31, 21) ## rdIndexReg(1, 0) ## 0.U(19.W)
  io.addrWrite := baseAddr.U(31, 21) ## wrIndexReg(1, 0) ## 0.U(19.W)

  printf(p"PageFlipper(rdIndex: $rdIndexReg, wrIndex: $wrIndexReg)\n")
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
