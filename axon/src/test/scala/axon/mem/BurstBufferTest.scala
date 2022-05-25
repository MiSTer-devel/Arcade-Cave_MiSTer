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

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class BurstBufferTest extends AnyFlatSpec with ChiselScalatestTester with Matchers {
  private def mkBuffer(inDataWidth: Int, outDataWidth: Int, burstLength: Int = 1) = new BurstBuffer(
    inAddrWidth = 8,
    inDataWidth = inDataWidth,
    outAddrWidth = 8,
    outDataWidth = outDataWidth,
    burstLength = burstLength
  )

  it should "buffer data (8:32)" in {
    test(mkBuffer(8, 32)) { dut =>
      dut.io.in.wr.poke(true)

      // write 0
      dut.io.in.waitReq.expect(false)
      dut.io.in.addr.poke(0x00)
      dut.io.in.din.poke(0x12)
      dut.io.out.wr.expect(false)
      dut.clock.step()

      // write 1
      dut.io.in.waitReq.expect(false)
      dut.io.in.addr.poke(0x01)
      dut.io.in.din.poke(0x34)
      dut.io.out.wr.expect(false)
      dut.clock.step()

      // write 2
      dut.io.in.waitReq.expect(false)
      dut.io.in.addr.poke(0x02)
      dut.io.in.din.poke(0x56)
      dut.io.out.wr.expect(false)
      dut.clock.step()

      // write 3
      dut.io.in.waitReq.expect(false)
      dut.io.in.addr.poke(0x03)
      dut.io.in.din.poke(0x78)
      dut.io.out.wr.expect(false)
      dut.clock.step()

      // wait for burst
      dut.io.in.waitReq.expect(true)
      dut.io.out.waitReq.poke(true)
      dut.io.out.wr.expect(true)
      dut.io.out.burstLength.expect(1)
      dut.io.out.addr.expect(0x00)
      dut.io.out.din.expect("h_78563412".U)
      dut.io.out.mask.expect(0xf)
      dut.clock.step()

      // burst 0
      dut.io.in.waitReq.expect(true)
      dut.io.out.burstDone.poke(true)
      dut.io.out.wr.expect(true)
      dut.io.out.addr.expect(0x00)
      dut.io.out.din.expect("h_78563412".U)
      dut.clock.step()

      // write 4
      dut.io.in.waitReq.expect(false)
      dut.io.in.addr.poke(0x04)
      dut.io.in.din.poke(0x90)
      dut.io.out.burstDone.poke(false)
      dut.io.out.wr.expect(false)
      dut.clock.step()

      // write 5
      dut.io.in.waitReq.expect(false)
      dut.io.in.addr.poke(0x05)
      dut.io.in.din.poke(0xab)
      dut.io.out.wr.expect(false)
      dut.clock.step()

      // write 6
      dut.io.in.waitReq.expect(false)
      dut.io.in.addr.poke(0x06)
      dut.io.in.din.poke(0xcd)
      dut.io.out.wr.expect(false)
      dut.clock.step()

      // write 7
      dut.io.in.waitReq.expect(false)
      dut.io.in.addr.poke(0x07)
      dut.io.in.din.poke(0xef)
      dut.io.out.wr.expect(false)
      dut.clock.step()

      // burst 1
      dut.io.in.waitReq.expect(true)
      dut.io.out.burstDone.poke(true)
      dut.io.out.wr.expect(true)
      dut.io.out.addr.expect(0x04)
      dut.io.out.din.expect("h_efcdab90".U)
    }
  }

  it should "buffer data (32:8)" in {
    test(mkBuffer(32, 8, burstLength = 4)) { dut =>
      dut.io.in.wr.poke(true)

      // write 0
      dut.io.in.waitReq.expect(false)
      dut.io.in.addr.poke(0x00)
      dut.io.in.din.poke("h_78563412".U)
      dut.io.out.wr.expect(false)
      dut.clock.step()

      // wait for burst
      dut.io.in.waitReq.expect(true)
      dut.io.out.waitReq.poke(true)
      dut.io.out.wr.expect(true)
      dut.io.out.burstLength.expect(4)
      dut.io.out.addr.expect(0x00)
      dut.io.out.din.expect(0x12)
      dut.io.out.mask.expect(0x1)

      // burst 0
      dut.io.in.waitReq.expect(true)
      dut.io.out.waitReq.poke(false)
      dut.io.out.wr.expect(true)
      dut.io.out.addr.expect(0x00)
      dut.io.out.din.expect(0x12)
      dut.clock.step()

      // burst 1
      dut.io.in.waitReq.expect(true)
      dut.io.out.wr.expect(true)
      dut.io.out.addr.expect(0x00)
      dut.io.out.din.expect(0x34)
      dut.clock.step()

      // burst 2
      dut.io.in.waitReq.expect(true)
      dut.io.out.wr.expect(true)
      dut.io.out.addr.expect(0x00)
      dut.io.out.din.expect(0x56)
      dut.clock.step()

      // burst 3
      dut.io.in.waitReq.expect(true)
      dut.io.out.burstDone.poke(true)
      dut.io.out.wr.expect(true)
      dut.io.out.addr.expect(0x00)
      dut.io.out.din.expect(0x78)
      dut.clock.step()

      // write 1
      dut.io.in.waitReq.expect(false)
      dut.io.in.addr.poke(0x04)
      dut.io.in.din.poke("h_efcdab90".U)
      dut.io.out.burstDone.poke(false)
      dut.io.out.wr.expect(false)
      dut.clock.step()

      // burst 4
      dut.io.in.waitReq.expect(true)
      dut.io.out.wr.expect(true)
      dut.io.out.addr.expect(0x04)
      dut.io.out.din.expect(0x90)
      dut.clock.step()

      // burst 5
      dut.io.in.waitReq.expect(true)
      dut.io.out.wr.expect(true)
      dut.io.out.addr.expect(0x04)
      dut.io.out.din.expect(0xab)
      dut.clock.step()

      // burst 6
      dut.io.in.waitReq.expect(true)
      dut.io.out.wr.expect(true)
      dut.io.out.addr.expect(0x04)
      dut.io.out.din.expect(0xcd)
      dut.clock.step()

      // burst 7
      dut.io.in.waitReq.expect(true)
      dut.io.out.burstDone.poke(true)
      dut.io.out.wr.expect(true)
      dut.io.out.addr.expect(0x04)
      dut.io.out.din.expect(0xef)
    }
  }
}
