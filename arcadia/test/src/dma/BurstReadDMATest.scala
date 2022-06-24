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

package arcadia.dma

import arcadia.mem.dma.{BurstReadDMA, Config}
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

trait BurstReadDMATestHelpers {
  protected def mkDMA() = new BurstReadDMA(Config(depth = 8, burstLength = 4))
}

class BurstReadDMATest extends AnyFlatSpec with ChiselScalatestTester with Matchers with BurstReadDMATestHelpers {
  it should "copy data from a bursted input memory to an asynchronous output memory" in {
    test(mkDMA()) { dut =>
      // start
      dut.io.start.poke(true)
      dut.io.in.rd.expect(false)
      dut.io.out.wr.expect(false)
      dut.clock.step()

      // wait for read
      dut.io.start.poke(false)
      dut.io.busy.expect(true)
      dut.io.in.waitReq.poke(true)
      dut.io.in.rd.expect(true)
      dut.io.in.addr.expect(0x00)
      dut.io.out.wr.expect(false)
      dut.clock.step()

      // read 0
      dut.io.in.waitReq.poke(false)
      dut.io.in.rd.expect(true)
      dut.io.in.addr.expect(0x00)
      dut.io.out.wr.expect(false)
      dut.clock.step()

      // wait for valid data
      dut.io.in.rd.expect(false)
      dut.io.out.wr.expect(false)
      dut.clock.step()

      // valid data
      dut.io.in.valid.poke(true)
      dut.io.in.dout.poke(0x10)
      dut.io.in.rd.expect(false)
      dut.io.out.wr.expect(false)
      dut.clock.step()

      // write 0
      dut.io.in.dout.poke(0x11)
      dut.io.in.rd.expect(false)
      dut.io.out.wr.expect(true)
      dut.io.out.addr.expect(0x00)
      dut.io.out.din.expect(0x10)
      dut.clock.step()

      // write 1
      dut.io.in.dout.poke(0x12)
      dut.io.in.rd.expect(false)
      dut.io.out.wr.expect(true)
      dut.io.out.addr.expect(0x08)
      dut.io.out.din.expect(0x11)
      dut.clock.step()

      // write 2
      dut.io.in.burstDone.poke(true)
      dut.io.in.dout.poke(0x13)
      dut.io.in.rd.expect(false)
      dut.io.out.wr.expect(true)
      dut.io.out.addr.expect(0x10)
      dut.io.out.din.expect(0x12)
      dut.clock.step()

      // read 1, wait for write
      dut.io.in.valid.poke(false)
      dut.io.in.burstDone.poke(false)
      dut.io.in.rd.expect(true)
      dut.io.in.addr.expect(0x20)
      dut.io.out.waitReq.poke(true)
      dut.io.out.wr.expect(true)
      dut.io.out.addr.expect(0x18)
      dut.io.out.din.expect(0x13)
      dut.clock.step()

      // write 3
      dut.io.in.valid.poke(true)
      dut.io.in.dout.poke(0x14)
      dut.io.in.rd.expect(false)
      dut.io.out.waitReq.poke(false)
      dut.io.out.wr.expect(true)
      dut.io.out.addr.expect(0x18)
      dut.io.out.din.expect(0x13)
      dut.clock.step()

      // write 4
      dut.io.in.rd.expect(false)
      dut.io.in.dout.poke(0x15)
      dut.io.out.wr.expect(true)
      dut.io.out.addr.expect(0x20)
      dut.io.out.din.expect(0x14)
      dut.clock.step()

      // write 5
      dut.io.in.dout.poke(0x16)
      dut.io.in.rd.expect(false)
      dut.io.out.wr.expect(true)
      dut.io.out.addr.expect(0x28)
      dut.io.out.din.expect(0x15)
      dut.clock.step()

      // write 6
      dut.io.in.burstDone.poke(true)
      dut.io.in.dout.poke(0x17)
      dut.io.in.rd.expect(false)
      dut.io.out.wr.expect(true)
      dut.io.out.addr.expect(0x30)
      dut.io.out.din.expect(0x16)
      dut.clock.step()

      // write 7
      dut.io.in.burstDone.poke(false)
      dut.io.in.valid.poke(false)
      dut.io.in.rd.expect(false)
      dut.io.out.wr.expect(true)
      dut.io.out.addr.expect(0x38)
      dut.io.out.din.expect(0x17)
      dut.clock.step()

      // done
      dut.io.in.rd.expect(false)
      dut.io.out.wr.expect(false)
      dut.clock.step()

      dut.io.busy.expect(false)
    }
  }
}
