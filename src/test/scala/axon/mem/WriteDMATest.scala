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

package axon.mem

import axon.mem.dma.{Config, WriteDMA}
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

trait WriteDMATestHelpers {
  protected def mkDMA() = new WriteDMA(Config(depth = 8, burstCount = 4))
}

class WriteDMATest extends AnyFlatSpec with ChiselScalatestTester with Matchers with WriteDMATestHelpers {
  it should "assert the busy signal during a transfer" in {
    test(mkDMA()) { dut =>
      dut.io.enable.poke(true)
      dut.io.start.poke(true)
      dut.io.in.burstDone.poke(true)
      dut.io.busy.expect(false)
      dut.clock.step()
      dut.io.busy.expect(true)
      dut.clock.step(9)
      dut.io.busy.expect(false)
      dut.clock.step()
      dut.io.busy.expect(true)
    }
  }

  it should "not start a transfer when the enable signal is deasserted" in {
    test(mkDMA()) { dut =>
      dut.io.enable.poke(false)
      dut.io.start.poke(true)
      dut.clock.step(10)

      dut.io.in.rd.expect(false)
      dut.io.out.wr.expect(false)
      dut.clock.step()

      dut.io.enable.poke(true)
      dut.io.in.rd.expect(true)
      dut.io.in.addr.expect(0)
      dut.clock.step()
    }
  }

  it should "copy data from the bursted input memory to the output memory" in {
    test(mkDMA()) { dut =>
      // Start transfer
      dut.io.enable.poke(true)
      dut.io.start.poke(true)
      dut.io.in.rd.expect(false)
      dut.io.out.wr.expect(false)
      dut.clock.step()

      // Wait for read request
      dut.io.in.waitReq.poke(true)
      dut.io.in.rd.expect(true)
      dut.io.in.addr.expect(0)
      dut.io.out.wr.expect(false)
      dut.clock.step()

      // Effective read
      dut.io.in.waitReq.poke(false)
      dut.io.in.rd.expect(true)
      dut.io.in.addr.expect(0)
      dut.io.out.wr.expect(false)
      dut.clock.step()

      // Wait for valid data
      dut.io.in.rd.expect(false)
      dut.io.out.wr.expect(false)
      dut.clock.step()

      // First word
      dut.io.in.rd.expect(false)
      dut.io.in.valid.poke(true)
      dut.io.in.dout.poke(0x10)
      dut.io.out.wr.expect(false)
      dut.clock.step()

      // Word 0
      dut.io.in.rd.expect(false)
      dut.io.in.dout.poke(0x11)
      dut.io.out.wr.expect(true)
      dut.io.out.addr.expect(0)
      dut.io.out.din.expect(0x10)
      dut.clock.step()

      // Word 1
      dut.io.in.rd.expect(false)
      dut.io.in.dout.poke(0x12)
      dut.io.out.wr.expect(true)
      dut.io.out.addr.expect(1)
      dut.io.out.din.expect(0x11)
      dut.clock.step()

      // Word 2
      dut.io.in.burstDone.poke(true)
      dut.io.in.rd.expect(false)
      dut.io.in.dout.poke(0x13)
      dut.io.out.wr.expect(true)
      dut.io.out.addr.expect(2)
      dut.io.out.din.expect(0x12)
      dut.clock.step()

      // Wait for write request
      dut.io.in.burstDone.poke(false)
      dut.io.in.rd.expect(true)
      dut.io.in.addr.expect(0x20)
      dut.io.in.dout.poke(0x14)
      dut.io.out.waitReq.poke(true)
      dut.io.out.wr.expect(true)
      dut.io.out.addr.expect(3)
      dut.io.out.din.expect(0x13)
      dut.clock.step()

      // Word 3
      dut.io.out.waitReq.poke(false)
      dut.io.in.rd.expect(false)
      dut.io.in.dout.poke(0x15)
      dut.io.out.wr.expect(true)
      dut.io.out.addr.expect(3)
      dut.io.out.din.expect(0x13)
      dut.clock.step()

      // Word 4
      dut.io.in.rd.expect(false)
      dut.io.in.dout.poke(0x16)
      dut.io.out.wr.expect(true)
      dut.io.out.addr.expect(4)
      dut.io.out.din.expect(0x14)
      dut.clock.step()

      // Word 5
      dut.io.in.burstDone.poke(true)
      dut.io.in.dout.poke(0x17)
      dut.io.in.rd.expect(false)
      dut.io.out.wr.expect(true)
      dut.io.out.addr.expect(5)
      dut.io.out.din.expect(0x15)
      dut.clock.step()

      // Word 6
      dut.io.in.burstDone.poke(false)
      dut.io.in.rd.expect(false)
      dut.io.out.wr.expect(true)
      dut.io.out.addr.expect(6)
      dut.io.out.din.expect(0x16)
      dut.clock.step()

      // Word 7
      dut.io.in.rd.expect(false)
      dut.io.out.wr.expect(true)
      dut.io.out.addr.expect(7)
      dut.io.out.din.expect(0x17)
      dut.clock.step()

      // Done
      dut.io.in.rd.expect(false)
      dut.io.out.wr.expect(false)
      dut.clock.step()
    }
  }
}
