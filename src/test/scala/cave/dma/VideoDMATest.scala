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

import chisel3._
import chiseltest._
import org.scalatest._
import flatspec.AnyFlatSpec
import matchers.should.Matchers

trait VideoDMATestHelpers {
  protected def mkDMA() = new VideoDMA(addr = 1, numWords = 8, burstLength = 4)
}

class VideoDMATest extends AnyFlatSpec with ChiselScalatestTester with Matchers with VideoDMATestHelpers {
  it should "deassert the ready signal during a transfer" in {
    test(mkDMA()) { dut =>
      dut.io.enable.poke(true)
      dut.io.pixelData.ready.poke(true)
      dut.io.ready.expect(true)
      dut.clock.step()
      dut.io.ready.expect(false)
      dut.io.ddr.burstDone.poke(true)
      dut.clock.step(3)
      dut.io.ready.expect(true)
    }
  }

  it should "assert the read enable signal at the start of a transfer" in {
    test(mkDMA()) { dut =>
      dut.io.enable.poke(true)
      dut.io.pixelData.ready.poke(true)
      dut.io.ddr.rd.expect(true)
      dut.clock.step()
      dut.io.ddr.rd.expect(false)
    }
  }

  it should "not start a transfer when the enable signal is deasserted" in {
    test(mkDMA()) { dut =>
      dut.io.pixelData.ready.poke(true)
      dut.io.ddr.rd.expect(false)
      dut.io.enable.poke(true)
      dut.io.ddr.rd.expect(true)
    }
  }

  it should "not start a transfer when the wait signal is asserted" in {
    test(mkDMA()) { dut =>
      dut.io.enable.poke(true)
      dut.io.pixelData.ready.poke(true)
      dut.io.ddr.waitReq.poke(true)
      dut.io.ddr.rd.expect(true)
      dut.clock.step()
      dut.io.ddr.waitReq.poke(false)
      dut.io.ddr.rd.expect(true)
      dut.clock.step()
      dut.io.ddr.rd.expect(false)
    }
  }

  it should "apply the frame buffer index to the DDR address offset" in {
    test(mkDMA()) { dut =>
      dut.io.enable.poke(true)
      dut.io.ddr.addr.expect(0x01.U)
      dut.io.frameBufferIndex.poke(1)
      dut.io.ddr.addr.expect(0x41.U)
    }
  }

  it should "read frame buffer data from DDR memory" in {
    test(mkDMA()) { dut =>
      dut.io.enable.poke(true)
      dut.io.pixelData.ready.poke(true)

      // Burst 1
      dut.io.ddr.rd.expect(true)
      dut.io.ddr.addr.expect(0x01.U)
      dut.clock.step()
      dut.io.ddr.rd.expect(false)
      0.to(3).foreach { n =>
        if (n == 3) dut.io.ddr.burstDone.poke(true)
        dut.io.ddr.valid.poke(true)
        dut.io.ddr.dout.poke(n)
        dut.io.pixelData.valid.expect(true)
        dut.io.pixelData.bits.expect(n.U)
        dut.clock.step()
      }
      dut.io.ddr.burstDone.poke(false)

      // Burst 2
      dut.io.ddr.rd.expect(true)
      dut.io.ddr.addr.expect(0x21.U)
      dut.clock.step()
      dut.io.ddr.rd.expect(false)
      0.to(3).foreach { n =>
        dut.io.ddr.valid.poke(true)
        dut.io.ddr.dout.poke(n)
        dut.io.pixelData.valid.expect(true)
        dut.io.pixelData.bits.expect(n.U)
        dut.clock.step()
      }
    }
  }
}
