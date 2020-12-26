/*
 *    __   __     __  __     __         __
 *   /\ "-.\ \   /\ \/\ \   /\ \       /\ \
 *   \ \ \-.  \  \ \ \_\ \  \ \ \____  \ \ \____
 *    \ \_\\"\_\  \ \_____\  \ \_____\  \ \_____\
 *     \/_/ \/_/   \/_____/   \/_____/   \/_____/
 *    ______     ______       __     ______     ______     ______
 *   /\  __ \   /\  == \     /\ \   /\  ___\   /\  ___\   /\__  _\
 *   \ \ \/\ \  \ \  __<    _\_\ \  \ \  __\   \ \ \____  \/_/\ \/
 *    \ \_____\  \ \_____\ /\_____\  \ \_____\  \ \_____\    \ \_\
 *     \/_____/   \/_____/ \/_____/   \/_____/   \/_____/     \/_/
 *
 *  https://joshbassett.info
 *  https://twitter.com/nullobject
 *  https://github.com/nullobject
 *
 *  Copyright (c) 2020 Josh Bassett
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
 */

package axon.util

import chisel3._

/**
 * A PISO (parallel-in, serial-out) used to buffer data.
 *
 * @param n The number of elements.
 * @param width The data width.
 * @param almostEmptyThreshold The threshold at which the [[isAlmostEmpty]] flag will be asserted.
 */
class PISO(n: Int, width: Int, almostEmptyThreshold: Int = 1) extends Module {
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
  val pisoAlmostEmpty = pisoCounterReg === almostEmptyThreshold.U

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
