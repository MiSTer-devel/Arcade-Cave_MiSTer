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

package cave

import axon.mem.ValidReadMemIO
import chisel3._

/**
 * Transfers data between clock domains.
 *
 * @param addrWidth The width of the address bus.
 * @param dataWidth The width of the data bus.
 */
class DataFreezer(addrWidth: Int, dataWidth: Int) extends Module {
  val io = IO(new Bundle {
    /** Target clock domain */
    val targetClock = Input(Clock())
    /** Target reset */
    val targetReset = Input(Reset())
    /** Input port */
    val in = Flipped(ValidReadMemIO(addrWidth, dataWidth))
    /** Output port */
    val out = ValidReadMemIO(addrWidth, dataWidth)
  })

  class DataFreezerBlackBox extends BlackBox(Map(
    "WIDTH_A" -> addrWidth,
    "WIDTH_B" -> dataWidth
  )) {
    val io = IO(new Bundle {
      val slow_rst_i = Input(Reset())
      val slow_clk_i = Input(Clock())
      val fast_rst_i = Input(Reset())
      val fast_clk_i = Input(Clock())
      val data_sc_i = Input(Bits(addrWidth.W))
      val write_sc_i = Input(Bool())
      val data_sc_o = Output(Bits(dataWidth.W))
      val valid_sc_o = Output(Bool())
      val data_fc_i = Input(Bits(dataWidth.W))
      val write_fc_i = Input(Bool())
      val data_fc_o = Output(Bits(addrWidth.W))
      val valid_fc_o = Output(Bool())
    })

    override def desiredName = "data_freezer"
  }

  val freezer = Module(new DataFreezerBlackBox)
  freezer.io.fast_rst_i := reset
  freezer.io.fast_clk_i := clock
  freezer.io.slow_rst_i := io.targetReset
  freezer.io.slow_clk_i := io.targetClock

  // Slow clock domain
  freezer.io.data_sc_i := io.in.addr
  freezer.io.write_sc_i := io.in.rd
  io.in.dout := freezer.io.data_sc_o
  io.in.valid := freezer.io.valid_sc_o

  // Fast clock domain
  io.out.addr := freezer.io.data_fc_o
  io.out.rd := freezer.io.valid_fc_o
  freezer.io.data_fc_i := io.out.dout
  freezer.io.write_fc_i := io.out.valid
}
