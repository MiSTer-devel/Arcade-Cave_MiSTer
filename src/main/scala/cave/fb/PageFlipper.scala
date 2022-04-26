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
 * Generates the read/write page addresses for the frame buffer.
 *
 * Triple buffering is be used by default, for buttery-smooth 60Hz HDMI output. When low latency
 * mode is enabled, then double buffering is used instead.
 *
 * @param baseAddr The base memory address of the frame buffer.
 */
class PageFlipper(baseAddr: Int) extends Module {
  val io = IO(new Bundle {
    /** Low latency mode (double buffering) */
    val lowLat = Input(Bool())
    /** Swap the read page */
    val swapRead = Input(Bool())
    /** Swap the write page */
    val swapWrite = Input(Bool())
    /** Read page address */
    val readPageAddr = Output(UInt(32.W))
    /** Write page address */
    val writePageAddr = Output(UInt(32.W))
  })

  // Registers
  val readIndexReg = RegInit(0.U(2.W))
  val writeIndexReg = RegInit(1.U(2.W))

  // Swap read index
  when(io.swapRead) {
    readIndexReg := Mux(io.lowLat,
      ~writeIndexReg(0),
      PageFlipper.nextIndex(readIndexReg, writeIndexReg)
    )
  }

  // Swap write index
  when(io.swapWrite) {
    writeIndexReg := Mux(io.lowLat,
      ~writeIndexReg(0),
      PageFlipper.nextIndex(writeIndexReg, readIndexReg)
    )
  }

  // Outputs
  io.readPageAddr := baseAddr.U(31, 21) ## readIndexReg(1, 0) ## 0.U(19.W)
  io.writePageAddr := baseAddr.U(31, 21) ## writeIndexReg(1, 0) ## 0.U(19.W)
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
