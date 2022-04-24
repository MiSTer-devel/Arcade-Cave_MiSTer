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

package axon.dma

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

trait WriteDMATestHelpers {
  protected def mkDMA() = new WriteDMA(DMAConfig(numWords = 8, burstLength = 4))
}

class WriteDMATest extends AnyFlatSpec with ChiselScalatestTester with Matchers with WriteDMATestHelpers {
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

  it should "assert the read enable signal during a transfer" in {
    test(mkDMA()) { dut =>
      dut.io.enable.poke(true)
      dut.io.start.poke(true)
      dut.clock.step()
      dut.io.start.poke(false)
      dut.io.ddr.rd.expect(true)
      dut.io.ddr.burstDone.poke(true)
      dut.clock.step(2)
      dut.io.ddr.rd.expect(false)
    }
  }

  it should "not start a transfer when the enable signal is deasserted" in {
    test(mkDMA()) { dut =>
      dut.io.start.poke(true)
      dut.clock.step()
      dut.io.ddr.rd.expect(false)
      dut.io.enable.poke(true)
      dut.clock.step()
      dut.io.ddr.rd.expect(true)
    }
  }

  it should "not increment the address when the wait signal is asserted" in {
    test(mkDMA()) { dut =>
      dut.io.enable.poke(true)
      dut.io.start.poke(true)
      dut.io.ddr.waitReq.poke(true)
      dut.io.dma.wr.expect(true)
      dut.io.dma.addr.expect(0.U)
      dut.clock.step()
      dut.io.ddr.waitReq.poke(false)
      dut.io.dma.wr.expect(true)
      dut.io.dma.addr.expect(1.U)
      dut.clock.step()
      dut.io.dma.wr.expect(true)
      dut.io.dma.addr.expect(2.U)
    }
  }

  it should "add the base address to the DDR memory address" in {
    test(mkDMA()) { dut =>
      dut.io.enable.poke(true)
      dut.io.ddr.addr.expect(0.U)
      dut.io.baseAddr.poke(1)
      dut.io.ddr.addr.expect(1.U)
    }
  }

  it should "copy data from DDR memory to the memory device" in {
    test(mkDMA()) { dut =>
      dut.io.enable.poke(true)
      dut.io.start.poke(true)
      dut.io.dma.wr.expect(true)
      dut.io.dma.addr.expect(0.U)
      dut.io.ddr.rd.expect(false)
      dut.clock.step()
      dut.io.start.poke(false)

      // Burst 1
      dut.io.dma.wr.expect(true)
      dut.io.dma.mask.expect(0xff.U)
      dut.io.ddr.rd.expect(true)
      dut.io.ddr.addr.expect(0.U)
      0.to(3).foreach { n =>
        if (n == 3) dut.io.ddr.burstDone.poke(true)
        dut.io.dma.addr.expect((n + 1).U)
        dut.io.ddr.dout.poke(n)
        dut.io.dma.din.expect(n.U)
        dut.clock.step()
      }
      dut.io.ddr.burstDone.poke(false)

      // Burst 2
      dut.io.dma.wr.expect(true)
      dut.io.dma.mask.expect(0xff.U)
      dut.io.ddr.rd.expect(true)
      dut.io.ddr.addr.expect(0x20.U)
      0.to(3).foreach { n =>
        if (n == 3) dut.io.ddr.burstDone.poke(true)
        dut.io.dma.addr.expect((n + 5).U)
        dut.io.ddr.dout.poke(n)
        dut.io.dma.din.expect(n.U)
        dut.clock.step()
      }
    }
  }
}
