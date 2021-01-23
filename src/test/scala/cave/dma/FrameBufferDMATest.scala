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
 * Copyright (c) 2021 Josh Bassett
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

import chisel3._
import chiseltest._
import org.scalatest._

trait FrameBufferDMATestHelpers {
  protected def mkDMA() = new FrameBufferDMA(addr = 1, numWords = 8, burstLength = 4)
}

class FrameBufferDMATest extends FlatSpec with ChiselScalatestTester with Matchers with FrameBufferDMATestHelpers {
  it should "deassert the ready signal during a transfer" in {
    test(mkDMA()) { dut =>
      dut.io.enable.poke(true.B)
      dut.io.start.poke(true.B)
      dut.io.ready.expect(true.B)
      dut.clock.step()
      dut.io.start.poke(false.B)
      dut.io.ddr.burstDone.poke(true.B)
      dut.io.ready.expect(false.B)
      dut.clock.step(2)
      dut.io.ready.expect(true.B)
    }
  }

  it should "assert the write enable signal during a transfer" in {
    test(mkDMA()) { dut =>
      dut.io.enable.poke(true.B)
      dut.io.start.poke(true.B)
      dut.clock.step()
      dut.io.start.poke(false.B)
      dut.io.ddr.wr.expect(true.B)
      dut.io.ddr.burstDone.poke(true.B)
      dut.clock.step(2)
      dut.io.ddr.wr.expect(false.B)
    }
  }

  it should "not start a transfer when the enable signal is deasserted" in {
    test(mkDMA()) { dut =>
      dut.io.start.poke(true.B)
      dut.clock.step()
      dut.io.ddr.wr.expect(false.B)
      dut.io.enable.poke(true.B)
      dut.clock.step()
      dut.io.ddr.wr.expect(true.B)
    }
  }

  it should "not increment the address when the wait signal is asserted" in {
    test(mkDMA()) { dut =>
      dut.io.enable.poke(true.B)
      dut.io.start.poke(true.B)
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

  it should "pack the pixel data" in {
    test(mkDMA()) { dut =>
      dut.io.enable.poke(true.B)
      dut.io.start.poke(true.B)
      dut.clock.step()
      dut.io.frameBufferDMA.dout.poke(0x112233445566L.U)
      dut.io.ddr.din.expect(0x0011223300445566L.U)
    }
  }

  it should "apply the frame buffer index to the DDR address offset" in {
    test(mkDMA()) { dut =>
      dut.io.enable.poke(true.B)
      dut.io.ddr.addr.expect(0x01.U)
      dut.io.frameBufferIndex.poke(1.U)
      dut.io.ddr.addr.expect(0x41.U)
    }
  }

  it should "write frame buffer data to DDR memory" in {
    test(mkDMA()) { dut =>
      dut.io.enable.poke(true.B)
      dut.io.start.poke(true.B)
      dut.io.frameBufferDMA.rd.expect(true.B)
      dut.io.frameBufferDMA.addr.expect(0.U)
      dut.io.ddr.wr.expect(false.B)
      dut.clock.step()
      dut.io.start.poke(false.B)

      // Burst 1
      dut.io.frameBufferDMA.rd.expect(true.B)
      dut.io.ddr.wr.expect(true.B)
      dut.io.ddr.addr.expect(0x01.U)
      dut.io.ddr.mask.expect(0xff.U)
      0.to(3).foreach { n =>
        if (n == 3) dut.io.ddr.burstDone.poke(true.B)
        dut.io.frameBufferDMA.addr.expect((n + 1).U)
        dut.io.frameBufferDMA.dout.poke(n.U)
        dut.io.ddr.din.expect(n.U)
        dut.clock.step()
      }
      dut.io.ddr.burstDone.poke(false.B)

      // Burst 2
      dut.io.frameBufferDMA.rd.expect(true.B)
      dut.io.ddr.wr.expect(true.B)
      dut.io.ddr.addr.expect(0x21.U)
      dut.io.ddr.mask.expect(0xff.U)
      0.to(3).foreach { n =>
        if (n == 3) dut.io.ddr.burstDone.poke(true.B)
        dut.io.frameBufferDMA.addr.expect((n + 5).U)
        dut.io.frameBufferDMA.dout.poke(n.U)
        dut.io.ddr.din.expect(n.U)
        dut.clock.step()
      }
    }
  }
}
