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

package cave.dma

import cave.FrameBufferDMA
import chisel3._
import chiseltest._
import org.scalatest._
import flatspec.AnyFlatSpec
import matchers.should.Matchers

trait FrameBufferDMATestHelpers {
  protected def mkDMA() = new FrameBufferDMA(baseAddr = 1, numWords = 8, burstLength = 4)
}

class FrameBufferDMATest extends AnyFlatSpec with ChiselScalatestTester with Matchers with FrameBufferDMATestHelpers {
  it should "deassert the ready signal during a transfer" in {
    test(mkDMA()) { dut =>
      dut.io.enable.poke(true)
      dut.io.start.poke(true)
      dut.io.ready.expect(true)
      dut.clock.step()
      dut.io.start.poke(false)
      dut.io.ddr.burstDone.poke(true)
      dut.io.ready.expect(false)
      dut.clock.step(2)
      dut.io.ready.expect(true)
    }
  }

  it should "assert the write enable signal during a transfer" in {
    test(mkDMA()) { dut =>
      dut.io.enable.poke(true)
      dut.io.start.poke(true)
      dut.clock.step()
      dut.io.start.poke(false)
      dut.io.ddr.wr.expect(true)
      dut.io.ddr.burstDone.poke(true)
      dut.clock.step(2)
      dut.io.ddr.wr.expect(false)
    }
  }

  it should "not start a transfer when the enable signal is deasserted" in {
    test(mkDMA()) { dut =>
      dut.io.start.poke(true)
      dut.clock.step()
      dut.io.ddr.wr.expect(false)
      dut.io.enable.poke(true)
      dut.clock.step()
      dut.io.ddr.wr.expect(true)
    }
  }

  it should "not increment the address when the wait signal is asserted" in {
    test(mkDMA()) { dut =>
      dut.io.enable.poke(true)
      dut.io.start.poke(true)
      dut.io.ddr.waitReq.poke(true)
      dut.io.dma.rd.expect(true)
      dut.io.dma.addr.expect(0.U)
      dut.clock.step()
      dut.io.ddr.waitReq.poke(false)
      dut.io.dma.rd.expect(true)
      dut.io.dma.addr.expect(1.U)
      dut.clock.step()
      dut.io.dma.rd.expect(true)
      dut.io.dma.addr.expect(2.U)
    }
  }

  it should "pack the pixel data" in {
    test(mkDMA()) { dut =>
      dut.io.enable.poke(true)
      dut.io.start.poke(true)
      dut.clock.step()
      dut.io.dma.dout.poke(0x112233445566L)
      dut.io.ddr.din.expect(0x0011223300445566L.U)
    }
  }

  it should "apply the frame buffer index to the DDR address offset" in {
    test(mkDMA()) { dut =>
      dut.io.enable.poke(true)
      dut.io.ddr.addr.expect(0x01.U)
      dut.io.page.poke(1)
      dut.io.ddr.addr.expect(0x41.U)
    }
  }

  it should "write frame buffer data to DDR memory" in {
    test(mkDMA()) { dut =>
      dut.io.enable.poke(true)
      dut.io.start.poke(true)
      dut.io.dma.rd.expect(true)
      dut.io.dma.addr.expect(0.U)
      dut.io.ddr.wr.expect(false)
      dut.clock.step()
      dut.io.start.poke(false)

      // Burst 1
      dut.io.dma.rd.expect(true)
      dut.io.ddr.wr.expect(true)
      dut.io.ddr.addr.expect(0x01.U)
      dut.io.ddr.mask.expect(0xff.U)
      0.to(3).foreach { n =>
        if (n == 3) dut.io.ddr.burstDone.poke(true)
        dut.io.dma.addr.expect((n + 1).U)
        dut.io.dma.dout.poke(n)
        dut.io.ddr.din.expect(n.U)
        dut.clock.step()
      }
      dut.io.ddr.burstDone.poke(false)

      // Burst 2
      dut.io.dma.rd.expect(true)
      dut.io.ddr.wr.expect(true)
      dut.io.ddr.addr.expect(0x21.U)
      dut.io.ddr.mask.expect(0xff.U)
      0.to(3).foreach { n =>
        if (n == 3) dut.io.ddr.burstDone.poke(true)
        dut.io.dma.addr.expect((n + 5).U)
        dut.io.dma.dout.poke(n)
        dut.io.ddr.din.expect(n.U)
        dut.clock.step()
      }
    }
  }
}
