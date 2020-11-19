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

package cave.dma

import chisel3._
import chiseltest._
import org.scalatest._

trait FrameBufferDMATestHelpers {
  protected def mkDMA() = new FrameBufferDMA(addr = 1, numWords = 8, burstLength = 4)
}

class FrameBufferDMATest extends FlatSpec with ChiselScalatestTester with Matchers with FrameBufferDMATestHelpers {
  it should "assert the write enable signal during a transfer" in {
    test(mkDMA()) { dut =>
      dut.io.frameBufferDMA.dmaStart.poke(true.B)
      dut.clock.step()
      dut.io.frameBufferDMA.dmaStart.poke(false.B)
      dut.io.ddr.wr.expect(true.B)
      dut.clock.step(8)
      dut.io.ddr.wr.expect(false.B)
      dut.io.frameBufferDMA.dmaDone.expect(true.B)
    }
  }

  it should "not increment the address while the wait signal is asserted" in {
    test(mkDMA()) { dut =>
      dut.io.frameBufferDMA.dmaStart.poke(true.B)
      dut.io.ddr.waitReq.poke(true.B)
      dut.io.frameBufferDMA.rd.expect(true.B)
      dut.io.frameBufferDMA.addr.expect(0.U)
      dut.clock.step()
      dut.io.ddr.waitReq.poke(false.B)
      dut.io.frameBufferDMA.rd.expect(true.B)
      dut.io.frameBufferDMA.addr.expect(1.U)
      dut.clock.step()
      dut.io.frameBufferDMA.rd.expect(true.B)
      dut.io.frameBufferDMA.addr.expect(2.U)
    }
  }

  it should "pad the pixel data" in {
    test(mkDMA()) { dut =>
      dut.io.frameBufferDMA.dmaStart.poke(true.B)
      dut.clock.step()
      dut.io.frameBufferDMA.dout.poke(0x22228889999C444L.U)
      dut.io.ddr.din.expect(0x1111222233334444L.U)
    }
  }

  it should "offset the DDR address when swapping frames" in {
    test(mkDMA()) { dut =>
      dut.io.ddr.addr.expect(0x01.U)
      dut.io.swap.poke(true.B)
      dut.io.ddr.addr.expect(0x41.U)
    }
  }

  it should "write frame buffer data to DDR memory" in {
    test(mkDMA()) { dut =>
      dut.io.frameBufferDMA.dmaStart.poke(true.B)
      dut.io.frameBufferDMA.rd.expect(true.B)
      dut.io.frameBufferDMA.addr.expect(0.U)
      dut.io.ddr.wr.expect(false.B)
      dut.clock.step()
      dut.io.frameBufferDMA.dmaStart.poke(false.B)

      // Burst 1
      dut.io.frameBufferDMA.rd.expect(true.B)
      dut.io.ddr.wr.expect(true.B)
      dut.io.ddr.addr.expect(0x01.U)
      dut.io.ddr.mask.expect(0xff.U)
      0.to(3).foreach { n =>
        dut.io.frameBufferDMA.addr.expect((n+1).U)
        dut.io.frameBufferDMA.dout.poke(n.U)
        dut.io.ddr.din.expect(n.U)
        dut.clock.step()
      }

      // Burst 2
      dut.io.frameBufferDMA.rd.expect(true.B)
      dut.io.ddr.wr.expect(true.B)
      dut.io.ddr.addr.expect(0x21.U)
      dut.io.ddr.mask.expect(0xff.U)
      0.to(3).foreach { n =>
        dut.io.frameBufferDMA.addr.expect((n+5).U)
        dut.io.frameBufferDMA.dout.poke(n.U)
        dut.io.ddr.din.expect(n.U)
        dut.clock.step()
      }
    }
  }
}
