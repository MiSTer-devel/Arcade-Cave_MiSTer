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

package cave.gpu

import chisel3._
import chiseltest._
import chiseltest.experimental.UncheckedClockPoke._
import org.scalatest._

class LayerInfoTest extends FlatSpec with ChiselScalatestTester with Matchers {
  it should "allow reading and writing to port A" in {
    test(new LayerInfo) { dut =>
      dut.io.portA.wr.poke(true.B)
      dut.io.portA.rd.poke(true.B)

      // Write
      dut.io.portA.mask.poke(0.U)
      dut.io.portA.din.poke(0x1234.U)
      dut.clock.step()
      dut.io.portA.dout.expect(0x0000.U)

      // Write
      dut.io.portA.mask.poke(1.U)
      dut.io.portA.din.poke(0x1234.U)
      dut.clock.step()
      dut.io.portA.dout.expect(0x0034.U)

      // Write
      dut.io.portA.mask.poke(2.U)
      dut.io.portA.din.poke(0x5678.U)
      dut.clock.step()
      dut.io.portA.dout.expect(0x5634.U)

      // Write
      dut.io.portA.mask.poke(3.U)
      dut.io.portA.din.poke(0xabcd.U)
      dut.clock.step()
      dut.io.portA.dout.expect(0xabcd.U)
    }
  }

  it should "allow reading from port B" in {
    test(new LayerInfo) { dut =>
      dut.io.portA.wr.poke(true.B)
      dut.io.portA.mask.poke(3.U)

      // Write
      dut.io.portA.addr.poke(0.U)
      dut.io.portA.din.poke(0x1234.U)
      dut.clock.step()
      dut.io.portA.addr.poke(1.U)
      dut.io.portA.din.poke(0x5678.U)
      dut.clock.step()
      dut.io.portA.addr.poke(2.U)
      dut.io.portA.din.poke(0xabcd.U)
      dut.clock.step()

      // Read
      dut.io.portB.rd.poke(true.B)
      dut.io.clockB.low()
      dut.io.clockB.high()
      dut.io.portB.dout.expect(0xabcd56781234L.U)
    }
  }
}
