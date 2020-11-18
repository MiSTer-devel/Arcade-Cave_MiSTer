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

import cave.FrameBufferReadDMA
import chisel3._
import chiseltest._
import org.scalatest._

trait FrameBufferReadDMATestHelpers {
  protected def mkDMA() = new FrameBufferReadDMA(addr = 1, numWords = 8, burstLength = 4)
}

class FrameBufferReadDMATest extends FlatSpec with ChiselScalatestTester with Matchers with FrameBufferReadDMATestHelpers {
  it should "assert the read enable signal at the start of a transfer" in {
    test(mkDMA()) { dut =>
      dut.io.pixelData.ready.poke(true.B)
      dut.io.ddr.rd.expect(true.B)
      dut.clock.step()
      dut.io.ddr.valid.poke(true.B)
      dut.io.ddr.rd.expect(false.B)
      dut.clock.step(8)
      dut.io.done.expect(true.B)
    }
  }

  it should "not start a transfer while the wait signal is asserted" in {
    test(mkDMA()) { dut =>
      dut.io.pixelData.ready.poke(true.B)
      dut.io.ddr.waitReq.poke(true.B)
      dut.io.ddr.rd.expect(true.B)
      dut.clock.step()
      dut.io.ddr.waitReq.poke(false.B)
      dut.io.ddr.rd.expect(true.B)
      dut.clock.step()
      dut.io.ddr.rd.expect(false.B)
    }
  }

  it should "offset the DDR address when swapping frames" in {
    test(mkDMA()) { dut =>
      dut.io.ddr.addr.expect(0x01.U)
      dut.io.swap.poke(true.B)
      dut.io.ddr.addr.expect(0x41.U)
    }
  }

  it should "read frame buffer data from DDR memory" in {
    test(mkDMA()) { dut =>
      dut.io.pixelData.ready.poke(true.B)

      // Burst 1
      dut.io.ddr.rd.expect(true.B)
      dut.io.ddr.addr.expect(0x01.U)
      dut.clock.step()
      dut.io.ddr.rd.expect(false.B)
      0.to(3).foreach { n =>
        dut.io.ddr.valid.poke(true.B)
        dut.io.ddr.dout.poke(n.U)
        dut.io.pixelData.valid.expect(true.B)
        dut.io.pixelData.bits.expect(n.U)
        dut.clock.step()
      }

      // Burst 2
      dut.io.ddr.rd.expect(true.B)
      dut.io.ddr.addr.expect(0x21.U)
      dut.clock.step()
      dut.io.ddr.rd.expect(false.B)
      0.to(3).foreach { n =>
        dut.io.ddr.valid.poke(true.B)
        dut.io.ddr.dout.poke(n.U)
        dut.io.pixelData.valid.expect(true.B)
        dut.io.pixelData.bits.expect(n.U)
        dut.clock.step()
      }
    }
  }
}
