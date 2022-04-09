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

package axon.mem

import chisel3._
import chiseltest._
import org.scalatest._
import flatspec.AnyFlatSpec
import matchers.should.Matchers

trait DDRTestHelpers {
  protected val ddrConfig = DDRConfig(addrWidth = 16, dataWidth = 16)

  protected def mkDDR(config: DDRConfig = ddrConfig) = new DDR(config)
}

class DDRTest extends AnyFlatSpec with ChiselScalatestTester with Matchers with DDRTestHelpers {
  behavior of "read"

  it should "read from DDR memory (burst=1)" in {
    test(mkDDR()) { dut =>
      // Request
      dut.io.mem.burstLength.poke(1)
      dut.io.mem.rd.poke(true)
      dut.io.mem.addr.poke(1)
      dut.io.ddr.rd.expect(true)
      dut.io.ddr.addr.expect(1.U)
      dut.clock.step()
      dut.io.ddr.rd.expect(false)

      // Data
      dut.io.mem.rd.poke(false)
      dut.io.ddr.valid.poke(true)
      dut.io.ddr.dout.poke(0x1234)
      dut.io.mem.burstDone.expect(true)
      dut.io.mem.valid.expect(true)
      dut.io.mem.dout.expect(0x1234.U)
      dut.clock.step()

      // Done
      dut.io.ddr.rd.expect(false)
      dut.io.mem.burstDone.expect(false)
    }
  }

  it should "read from DDR memory (burst=2)" in {
    test(mkDDR()) { dut =>
      // Request
      dut.io.mem.burstLength.poke(2)
      dut.io.mem.rd.poke(true)
      dut.io.mem.addr.poke(1)
      dut.io.ddr.rd.expect(true)
      dut.io.ddr.addr.expect(1.U)
      dut.clock.step()
      dut.io.ddr.rd.expect(false)

      // Data
      dut.io.mem.rd.poke(false)
      dut.io.ddr.valid.poke(true)
      dut.io.ddr.dout.poke(0x1234)
      dut.io.mem.valid.expect(true)
      dut.io.mem.dout.expect(0x1234.U)
      dut.clock.step()
      dut.io.ddr.dout.poke(0x5678)
      dut.io.mem.burstDone.expect(true)
      dut.io.mem.valid.expect(true)
      dut.io.mem.dout.expect(0x5678.U)
      dut.clock.step()

      // Done
      dut.io.ddr.rd.expect(false)
      dut.io.mem.burstDone.expect(false)
    }
  }

  it should "latch the burst length" in {
    test(mkDDR()) { dut =>
      dut.io.mem.burstLength.poke(2)
      dut.io.mem.rd.poke(true)
      dut.io.ddr.burstLength.expect(2.U)
      dut.clock.step()
      dut.io.mem.burstLength.poke(1)
      dut.io.ddr.valid.poke(true)
      dut.io.ddr.burstLength.expect(2.U)
      dut.clock.step(2)
      dut.io.ddr.burstLength.expect(1.U)
    }
  }

  it should "hold the burst counter when the wait signal is asserted" in {
    test(mkDDR()) { dut =>
      dut.io.mem.burstLength.poke(2)
      dut.io.mem.rd.poke(true)
      dut.io.ddr.waitReq.poke(true)
      dut.io.debug.burstCounter.expect(0.U)
      dut.clock.step()
      dut.io.ddr.waitReq.poke(false)
      dut.io.ddr.valid.poke(true)
      dut.io.debug.burstCounter.expect(0.U)
      dut.clock.step()
      dut.io.debug.burstCounter.expect(0.U)
      dut.clock.step()
      dut.io.debug.burstCounter.expect(1.U)
      dut.clock.step()
      dut.io.debug.burstCounter.expect(0.U)
    }
  }

  behavior of "write"

  it should "write to DDR memory (burst=1)" in {
    test(mkDDR()) { dut =>
      // Request
      dut.io.mem.burstLength.poke(1)
      dut.io.mem.wr.poke(true)
      dut.io.mem.addr.poke(1)
      dut.io.mem.din.poke(0x1234)
      dut.io.ddr.wr.expect(true)
      dut.io.ddr.addr.expect(1.U)
      dut.io.ddr.din.expect(0x1234.U)
      dut.clock.step()
      dut.io.mem.wr.poke(false)

      // Done
      dut.io.ddr.wr.expect(false)
      dut.io.mem.burstDone.expect(false)
    }
  }

  it should "write to DDR memory (burst=2)" in {
    test(mkDDR()) { dut =>
      // Request
      dut.io.mem.burstLength.poke(2)
      dut.io.mem.wr.poke(true)
      dut.io.mem.addr.poke(1)
      dut.io.mem.din.poke(0x1234)
      dut.io.ddr.wr.expect(true)
      dut.io.ddr.addr.expect(1.U)
      dut.io.ddr.din.expect(0x1234.U)
      dut.clock.step()

      // Data
      dut.io.mem.wr.poke(false)
      dut.io.mem.din.poke(0x5678)
      dut.io.ddr.wr.expect(true)
      dut.io.ddr.din.expect(0x5678.U)
      dut.io.mem.burstDone.expect(true)
      dut.clock.step()

      // Done
      dut.io.ddr.wr.expect(false)
      dut.io.mem.burstDone.expect(false)
    }
  }

  it should "latch the burst length" in {
    test(mkDDR()) { dut =>
      dut.io.mem.burstLength.poke(2)
      dut.io.mem.wr.poke(true)
      dut.io.ddr.burstLength.expect(2.U)
      dut.clock.step()
      dut.io.mem.burstLength.poke(1)
      dut.io.ddr.burstLength.expect(2.U)
      dut.clock.step(2)
      dut.io.ddr.burstLength.expect(1.U)
    }
  }

  it should "hold the burst counter when the wait signal is asserted" in {
    test(mkDDR()) { dut =>
      dut.io.mem.burstLength.poke(2)
      dut.io.mem.wr.poke(true)
      dut.io.ddr.waitReq.poke(true)
      dut.io.debug.burstCounter.expect(0.U)
      dut.clock.step()
      dut.io.ddr.waitReq.poke(false)
      dut.io.debug.burstCounter.expect(0.U)
      dut.clock.step()
      dut.io.debug.burstCounter.expect(1.U)
      dut.clock.step()
      dut.io.debug.burstCounter.expect(0.U)
    }
  }
}
