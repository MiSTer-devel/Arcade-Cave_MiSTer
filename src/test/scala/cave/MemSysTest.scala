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

package cave

import chisel3._
import chiseltest._
import org.scalatest._
import flatspec.AnyFlatSpec
import matchers.should.Matchers

trait MemSysTestHelpers {
  protected def waitForDownloadReady(dut: MemSys) =
    while (dut.io.ioctl.waitReq.peekBoolean()) { dut.clock.step() }
}

class MemSysTest extends AnyFlatSpec with ChiselScalatestTester with Matchers with MemSysTestHelpers {
  it should "write download data to memory" in {
    test(new MemSys) { dut =>
      waitForDownloadReady(dut)
      dut.io.ioctl.download.poke(true)

      // Download & fill
      dut.io.ioctl.wr.poke(true)
      dut.io.ioctl.addr.poke(0)
      dut.io.ioctl.dout.poke(0x1234)
      dut.clock.step()
      dut.io.ioctl.wr.poke(false)
      dut.clock.step(2)
      dut.io.ddr.rd.expect(true)
      dut.io.ddr.burstLength.expect(1.U)

      // DDR valid
      dut.io.ddr.valid.poke(true)
      dut.clock.step(2)
      dut.io.ddr.valid.poke(false)

      // Burst done
      dut.io.ddr.burstDone.poke(true)
      dut.clock.step()
      dut.io.ddr.burstDone.poke(false)

      // Download & evict
      dut.io.ioctl.wr.poke(true)
      dut.io.ioctl.addr.poke(8)
      dut.io.ioctl.dout.poke(0x5678)
      dut.clock.step()
      dut.io.ioctl.wr.poke(false)
      dut.clock.step(2)
      dut.io.ddr.wr.expect(true)
      dut.io.ddr.burstLength.expect(1.U)
    }
  }
}
