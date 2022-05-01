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
 *  Copyright (c) 2022 Josh Bassett
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
 * This module wraps an external single-port ROM module.
 *
 * @param addrWidth The width of the address bus.
 * @param dataWidth The width of the data bus.
 */
class SinglePortRom(addrWidth: Int, dataWidth: Int, depth: Int, initFile: String) extends Module {
  val io = IO(Flipped(ReadMemIO(addrWidth, dataWidth)))

  class WrappedSinglePortRam extends BlackBox(
    Map(
      "ADDR_WIDTH" -> addrWidth,
      "DATA_WIDTH" -> dataWidth,
      "DEPTH" -> depth,
      "INIT_FILE" -> initFile
    )
  ) {
    val io = IO(new Bundle {
      val clk = Input(Clock())
      val rd = Input(Bool())
      val addr = Input(UInt(addrWidth.W))
      val dout = Output(Bits(dataWidth.W))
    })

    override def desiredName = "single_port_rom"
  }

  val rom = Module(new WrappedSinglePortRam)
  rom.io.clk := clock
  rom.io.rd := io.rd
  rom.io.addr := io.addr
  io.dout := rom.io.dout
}
