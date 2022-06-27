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

package arcadia.mem.buffer

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

trait WordBufferTestHelpers {
  protected val bufferConfig = Config(
    inAddrWidth = 8,
    inDataWidth = 8,
    outAddrWidth = 8,
    outDataWidth = 16
  )

  protected def mkBuffer(config: Config) = new WordBuffer(config)
}

class WordBufferTest extends AnyFlatSpec with ChiselScalatestTester with Matchers with WordBufferTestHelpers {
  it should "buffer data (8:16)" in {
    test(mkBuffer(bufferConfig)) { dut =>
      dut.io.in.wr.poke(true)
      dut.io.in.mask.poke(0x1)

      // write 0
      dut.io.in.addr.poke(0x00)
      dut.io.in.din.poke(0x12)
      dut.io.out.wr.expect(false)
      dut.clock.step()

      // write 1
      dut.io.in.addr.poke(0x01)
      dut.io.in.din.poke(0x34)
      dut.io.out.wr.expect(false)
      dut.clock.step()

      // wait
      dut.io.in.addr.poke(0x02)
      dut.io.in.waitReq.expect(true)
      dut.io.out.wr.expect(true)
      dut.io.out.addr.expect(0x00)
      dut.io.out.mask.expect(0x3)
      dut.io.out.din.expect(0x3412)
      dut.clock.step()

      // write 2
      dut.io.in.addr.poke(0x02)
      dut.io.in.din.poke(0x56)
      dut.io.in.waitReq.expect(false)
      dut.io.out.wr.expect(false)
      dut.clock.step()

      // write 3
      dut.io.in.waitReq.expect(false)
      dut.io.in.addr.poke(0x03)
      dut.io.in.din.poke(0x78)
      dut.io.out.wr.expect(false)
      dut.clock.step()

      // wait
      dut.io.in.addr.poke(0x04)
      dut.io.in.waitReq.expect(true)
      dut.io.out.wr.expect(true)
      dut.io.out.addr.expect(0x02)
      dut.io.out.mask.expect(0x3)
      dut.io.out.din.expect(0x7856)
      dut.clock.step()

      // write 4
      dut.io.in.addr.poke(0x04)
      dut.io.in.din.poke(0x90)
      dut.io.in.waitReq.expect(false)
      dut.io.out.wr.expect(false)
      dut.clock.step()

      // write 5
      dut.io.in.addr.poke(0x05)
      dut.io.in.mask.poke(0x0)
      dut.io.in.din.poke(0xab)
      dut.io.in.waitReq.expect(false)
      dut.io.out.wr.expect(false)
      dut.clock.step()

      // wait
      dut.io.in.addr.poke(0x06)
      dut.io.in.waitReq.expect(true)
      dut.io.out.wr.expect(true)
      dut.io.out.addr.expect(0x04)
      dut.io.out.mask.expect(0x1)
      dut.io.out.din.expect(0xab90)
      dut.clock.step()
    }
  }

  it should "buffer data (16:64)" in {
    test(mkBuffer(bufferConfig.copy(inDataWidth = 16, outDataWidth = 64))) { dut =>
      dut.io.in.wr.poke(true)
      dut.io.in.mask.poke(0x03)

      // write 0
      dut.io.in.addr.poke(0x00)
      dut.io.in.din.poke(0x1234)
      dut.io.out.wr.expect(false)
      dut.clock.step()

      // write 1
      dut.io.in.addr.poke(0x02)
      dut.io.in.din.poke(0x5678)
      dut.io.out.wr.expect(false)
      dut.clock.step()

      // write 2
      dut.io.in.addr.poke(0x04)
      dut.io.in.din.poke(0x90ab)
      dut.io.out.wr.expect(false)
      dut.clock.step()

      // write 3
      dut.io.in.addr.poke(0x06)
      dut.io.in.din.poke(0xcdef)
      dut.io.out.wr.expect(false)
      dut.clock.step()

      // wait
      dut.io.in.addr.poke(0x08)
      dut.io.in.waitReq.expect(true)
      dut.io.out.wr.expect(true)
      dut.io.out.addr.expect(0x00)
      dut.io.out.mask.expect(0xff)
      dut.io.out.din.expect("h_cdef_90ab_5678_1234".U)
      dut.clock.step()

      // write 0
      dut.io.in.addr.poke(0x08)
      dut.io.in.din.poke(0x1234)
      dut.io.out.wr.expect(false)
      dut.clock.step()

      // write 1
      dut.io.in.addr.poke(0x0a)
      dut.io.in.din.poke(0x5678)
      dut.io.out.wr.expect(false)
      dut.clock.step()

      // write 2
      dut.io.in.addr.poke(0x0c)
      dut.io.in.din.poke(0x90ab)
      dut.io.out.wr.expect(false)
      dut.clock.step()

      // write 3
      dut.io.in.addr.poke(0x0e)
      dut.io.in.mask.poke(0x01)
      dut.io.in.din.poke(0xcdef)
      dut.io.out.wr.expect(false)
      dut.clock.step()

      // wait
      dut.io.in.addr.poke(0x10)
      dut.io.in.waitReq.expect(true)
      dut.io.out.wr.expect(true)
      dut.io.out.addr.expect(0x08)
      dut.io.out.mask.expect(0x7f)
      dut.io.out.din.expect("h_cdef_90ab_5678_1234".U)
      dut.clock.step()
    }
  }
}
