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

package axon.mem

import chisel3._

/**
 * A dual-port RAM with separate clocks for each port.
 *
 * Port A is read-write, and is clocked with the default clock. Port B is read-only, and is clocked with the [[clockB]]
 * input.
 *
 * @param addrWidthA The width of the port A address bus.
 * @param dataWidthA The width of the port A data bus.
 * @param addrWidthB The width of the port B address bus.
 * @param dataWidthB The width of the port B data bus.
 */
class TrueDualPortRam(addrWidthA: Int, dataWidthA: Int, addrWidthB: Int, dataWidthB: Int) extends Module {
  val io = IO(new Bundle {
    /** Clock B */
    val clockB = Input(Clock())
    /** Read-write port */
    val portA = Flipped(ReadWriteMemIO(addrWidthA, dataWidthA))
    /** Read-only port */
    val portB = Flipped(ReadMemIO(addrWidthB, dataWidthB))
  })

  class WrappedTrueDualPortRam extends BlackBox(Map(
      "ADDR_WIDTH_A" -> addrWidthA,
      "DATA_WIDTH_A" -> dataWidthA,
      "ADDR_WIDTH_B" -> addrWidthB,
      "DATA_WIDTH_B" -> dataWidthB
    )) {
    val io = IO(new Bundle {
      // port A
      val clk_a = Input(Clock())
      val rd_a = Input(Bool())
      val wr_a = Input(Bool())
      val addr_a = Input(UInt(addrWidthA.W))
      val mask_a = Input(Bits((dataWidthA/8).W))
      val din_a = Input(Bits(dataWidthA.W))
      val dout_a = Output(Bits(dataWidthA.W))

      // port B
      val clk_b = Input(Clock())
      val rd_b = Input(Bool())
      val addr_b = Input(UInt(addrWidthB.W))
      val dout_b = Output(Bits(dataWidthB.W))
    })

    override def desiredName = "true_dual_port_ram"
  }

  val ram = Module(new WrappedTrueDualPortRam)
  ram.io.clk_a := clock
  ram.io.rd_a := io.portA.rd
  ram.io.wr_a := io.portA.wr
  ram.io.addr_a := io.portA.addr
  ram.io.mask_a := io.portA.mask
  ram.io.din_a := io.portA.din
  io.portA.dout := ram.io.dout_a
  ram.io.clk_b := io.clockB
  ram.io.rd_b := io.portB.rd
  ram.io.addr_b := io.portB.addr
  io.portB.dout := ram.io.dout_b
}
