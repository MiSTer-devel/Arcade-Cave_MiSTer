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
 * This module wraps an external dual-port RAM module.
 *
 * @param addrWidthA The width of the port A address bus.
 * @param dataWidthA The width of the port A data bus.
 * @param depthA The optional depth of port A.
 * @param addrWidthB The width of the port B address bus.
 * @param dataWidthB The width of the port B data bus.
 * @param depthB The optional depth of port B.
 */
class DualPortRam(addrWidthA: Int,
                  dataWidthA: Int,
                  depthA: Option[Int] = None,
                  addrWidthB: Int,
                  dataWidthB: Int,
                  depthB: Option[Int] = None) extends Module {
  val io = IO(new Bundle {
    /** Write-only port */
    val portA = Flipped(WriteMemIO(addrWidthA, dataWidthA))
    /** Read-only port */
    val portB = Flipped(ReadMemIO(addrWidthB, dataWidthB))
  })

  private val depthA_ = depthA.getOrElse(0)
  private val depthB_ = depthB.getOrElse(0)

  class WrappedDualPortRam extends BlackBox(Map(
    "ADDR_WIDTH_A" -> addrWidthA,
    "DATA_WIDTH_A" -> dataWidthA,
    "DEPTH_A"      -> depthA_,
    "ADDR_WIDTH_B" -> addrWidthB,
    "DATA_WIDTH_B" -> dataWidthB,
    "DEPTH_B"      -> depthB_
  )) {
    val io = IO(new Bundle {
      val clk = Input(Clock())

      // port A
      val we_a = Input(Bool())
      val addr_a = Input(UInt(addrWidthA.W))
      val din_a = Input(Bits(dataWidthA.W))

      // port B
      val re_b = Input(Bool())
      val addr_b = Input(UInt(addrWidthB.W))
      val dout_b = Output(Bits(dataWidthB.W))
    })

    override def desiredName = "dual_port_ram"
  }

  val ram = Module(new WrappedDualPortRam)
  ram.io.clk := clock
  ram.io.we_a := io.portA.wr
  ram.io.addr_a := io.portA.addr
  ram.io.din_a := io.portA.din
  ram.io.re_b := io.portB.rd
  ram.io.addr_b := io.portB.addr
  io.portB.dout := ram.io.dout_b
}
