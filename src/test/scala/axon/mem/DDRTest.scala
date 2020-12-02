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
import chiseltest._
import org.scalatest._

trait DDRTestHelpers {
  protected val ddrConfig = DDRConfig()

  protected def mkDDR(config: DDRConfig = ddrConfig) = new DDR(config)
}

class DDRTest extends FlatSpec with ChiselScalatestTester with Matchers with DDRTestHelpers {
  behavior of "read"

  it should "read from DDR memory" in {
    test(mkDDR()) { dut =>
      // Request
      dut.io.mem.burstLength.poke(2.U)
      dut.io.mem.rd.poke(true.B)
      dut.io.mem.addr.poke(1.U)
      dut.io.ddr.rd.expect(true.B)
      dut.io.ddr.addr.expect(1.U)
      dut.clock.step()
      dut.io.ddr.rd.expect(false.B)

      // Data
      dut.io.mem.rd.poke(false.B)
      dut.io.ddr.valid.poke(true.B)
      dut.io.ddr.dout.poke(0x1234.U)
      dut.io.mem.valid.expect(true.B)
      dut.io.mem.dout.expect(0x1234.U)
      dut.clock.step()
      dut.io.ddr.dout.poke(0x5678.U)
      dut.io.mem.burstDone.expect(true.B)
      dut.io.mem.valid.expect(true.B)
      dut.io.mem.dout.expect(0x5678.U)
      dut.clock.step()

      // Done
      dut.io.mem.burstDone.expect(false.B)
      dut.io.mem.rd.expect(false.B)
    }
  }

  it should "latch the burst length" in {
    test(mkDDR()) { dut =>
      dut.io.mem.burstLength.poke(2.U)
      dut.io.mem.rd.poke(true.B)
      dut.io.ddr.burstLength.expect(2.U)
      dut.clock.step()
      dut.io.mem.burstLength.poke(1.U)
      dut.io.ddr.valid.poke(true.B)
      dut.io.ddr.burstLength.expect(2.U)
      dut.clock.step(2)
      dut.io.ddr.burstLength.expect(1.U)
    }
  }

  it should "hold the burst counter when the wait signal is asserted" in {
    test(mkDDR()) { dut =>
      dut.io.mem.burstLength.poke(2.U)
      dut.io.mem.rd.poke(true.B)
      dut.io.ddr.waitReq.poke(true.B)
      dut.io.ddr.valid.poke(true.B)
      dut.io.debug.burstCounter.expect(0.U)
      dut.clock.step()
      dut.io.ddr.waitReq.poke(false.B)
      dut.io.debug.burstCounter.expect(0.U)
      dut.clock.step()
      dut.io.debug.burstCounter.expect(1.U)
      dut.clock.step()
      dut.io.debug.burstCounter.expect(0.U)
    }
  }

  behavior of "write"

  it should "write to DDR memory" in {
    test(mkDDR()) { dut =>
      // Request
      dut.io.mem.burstLength.poke(2.U)
      dut.io.mem.wr.poke(true.B)
      dut.io.mem.addr.poke(1.U)
      dut.io.mem.din.poke(0x1234.U)
      dut.io.ddr.wr.expect(true.B)
      dut.io.ddr.addr.expect(1.U)
      dut.io.ddr.din.expect(0x1234.U)
      dut.clock.step()

      // Data
      dut.io.mem.wr.poke(false.B)
      dut.io.mem.din.poke(0x5678.U)
      dut.io.ddr.wr.expect(true.B)
      dut.io.ddr.din.expect(0x5678.U)
      dut.io.mem.burstDone.expect(true.B)
      dut.clock.step()

      // Done
      dut.io.mem.burstDone.expect(false.B)
      dut.io.ddr.wr.expect(false.B)
    }
  }

  it should "latch the burst length" in {
    test(mkDDR()) { dut =>
      dut.io.mem.burstLength.poke(2.U)
      dut.io.mem.wr.poke(true.B)
      dut.io.ddr.burstLength.expect(2.U)
      dut.clock.step()
      dut.io.mem.burstLength.poke(1.U)
      dut.io.ddr.burstLength.expect(2.U)
      dut.clock.step(2)
      dut.io.ddr.burstLength.expect(1.U)
    }
  }

  it should "hold the burst counter when the wait signal is asserted" in {
    test(mkDDR()) { dut =>
      dut.io.mem.burstLength.poke(2.U)
      dut.io.mem.wr.poke(true.B)
      dut.io.ddr.waitReq.poke(true.B)
      dut.io.debug.burstCounter.expect(0.U)
      dut.clock.step()
      dut.io.ddr.waitReq.poke(false.B)
      dut.io.debug.burstCounter.expect(0.U)
      dut.clock.step()
      dut.io.debug.burstCounter.expect(1.U)
      dut.clock.step()
      dut.io.debug.burstCounter.expect(0.U)
    }
  }
}
