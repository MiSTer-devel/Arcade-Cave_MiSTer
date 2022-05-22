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

import axon.mem.ddr.{DDR, Config}
import chiseltest._
import org.scalatest._
import flatspec.AnyFlatSpec
import matchers.should.Matchers

trait DDRTestHelpers {
  protected val ddrConfig = Config()

  protected def mkDDR(config: Config = ddrConfig) = new DDR(config)
}

class DDRTest extends AnyFlatSpec with ChiselScalatestTester with Matchers with DDRTestHelpers {
  behavior of "read"

  it should "read from DDR memory (burst=1)" in {
    test(mkDDR()) { dut =>
      // request
      dut.io.mem.burstLength.poke(1)
      dut.io.mem.rd.poke(true)
      dut.io.mem.addr.poke(1)
      dut.io.ddr.rd.expect(true)
      dut.io.ddr.addr.expect(1)
      dut.clock.step()
      dut.io.ddr.rd.expect(false)

      // data
      dut.io.mem.rd.poke(false)
      dut.io.ddr.valid.poke(true)
      dut.io.ddr.dout.poke(0x1234)
      dut.io.mem.burstDone.expect(true)
      dut.io.mem.valid.expect(true)
      dut.io.mem.dout.expect(0x1234)
      dut.clock.step()

      // done
      dut.io.ddr.rd.expect(false)
      dut.io.mem.burstDone.expect(false)
    }
  }

  it should "read from DDR memory (burst=2)" in {
    test(mkDDR()) { dut =>
      // request
      dut.io.mem.burstLength.poke(2)
      dut.io.mem.rd.poke(true)
      dut.io.mem.addr.poke(1)
      dut.io.ddr.rd.expect(true)
      dut.io.ddr.addr.expect(1)
      dut.clock.step()
      dut.io.ddr.rd.expect(false)

      // data
      dut.io.mem.rd.poke(false)
      dut.io.ddr.valid.poke(true)
      dut.io.ddr.dout.poke(0x1234)
      dut.io.mem.valid.expect(true)
      dut.io.mem.dout.expect(0x1234)
      dut.clock.step()
      dut.io.ddr.dout.poke(0x5678)
      dut.io.mem.burstDone.expect(true)
      dut.io.mem.valid.expect(true)
      dut.io.mem.dout.expect(0x5678)
      dut.clock.step()

      // done
      dut.io.ddr.rd.expect(false)
      dut.io.mem.burstDone.expect(false)
    }
  }

  it should "latch the burst length" in {
    test(mkDDR()) { dut =>
      dut.io.mem.burstLength.poke(2)
      dut.io.mem.rd.poke(true)
      dut.io.ddr.burstLength.expect(2)
      dut.clock.step()
      dut.io.mem.burstLength.poke(1)
      dut.io.ddr.valid.poke(true)
      dut.io.ddr.burstLength.expect(2)
      dut.clock.step(2)
      dut.io.ddr.burstLength.expect(1)
    }
  }

  it should "hold the burst counter when the wait signal is asserted" in {
    test(mkDDR()) { dut =>
      dut.io.mem.burstLength.poke(2)
      dut.io.mem.rd.poke(true)
      dut.io.ddr.waitReq.poke(true)
      dut.io.debug.burstCounter.expect(0)
      dut.clock.step()
      dut.io.ddr.waitReq.poke(false)
      dut.io.ddr.valid.poke(true)
      dut.io.debug.burstCounter.expect(0)
      dut.clock.step()
      dut.io.debug.burstCounter.expect(0)
      dut.clock.step()
      dut.io.debug.burstCounter.expect(1)
      dut.clock.step()
      dut.io.debug.burstCounter.expect(0)
    }
  }

  behavior of "write"

  it should "write to DDR memory (burst=1)" in {
    test(mkDDR()) { dut =>
      // request
      dut.io.mem.burstLength.poke(1)
      dut.io.mem.wr.poke(true)
      dut.io.mem.addr.poke(1)
      dut.io.mem.din.poke(0x1234)
      dut.io.ddr.wr.expect(true)
      dut.io.ddr.addr.expect(1)
      dut.io.ddr.din.expect(0x1234)
      dut.io.mem.burstDone.expect(true)
      dut.clock.step()
      dut.io.mem.wr.poke(false)

      // done
      dut.io.ddr.wr.expect(false)
      dut.io.mem.burstDone.expect(false)
    }
  }

  it should "write to DDR memory (burst=2)" in {
    test(mkDDR()) { dut =>
      // request
      dut.io.mem.burstLength.poke(2)
      dut.io.mem.wr.poke(true)
      dut.io.mem.addr.poke(1)
      dut.io.mem.din.poke(0x1234)
      dut.io.ddr.wr.expect(true)
      dut.io.ddr.addr.expect(1)
      dut.io.ddr.din.expect(0x1234)
      dut.clock.step()

      // data
      dut.io.mem.din.poke(0x5678)
      dut.io.ddr.wr.expect(true)
      dut.io.ddr.din.expect(0x5678)
      dut.io.mem.burstDone.expect(true)
      dut.clock.step()

      // done
      dut.io.mem.wr.poke(false)
      dut.io.ddr.wr.expect(false)
      dut.io.mem.burstDone.expect(false)
    }
  }

  it should "latch the burst length" in {
    test(mkDDR()) { dut =>
      dut.io.mem.burstLength.poke(2)
      dut.io.mem.wr.poke(true)
      dut.io.ddr.burstLength.expect(2)
      dut.clock.step()
      dut.io.mem.burstLength.poke(1)
      dut.io.ddr.burstLength.expect(2)
      dut.clock.step(2)
      dut.io.ddr.burstLength.expect(1)
    }
  }

  it should "hold the burst counter when the write signal is deasserted" in {
    test(mkDDR()) { dut =>
      dut.io.mem.burstLength.poke(2)
      dut.io.mem.wr.poke(true)
      dut.io.debug.burstCounter.expect(0)
      dut.clock.step()
      dut.io.mem.wr.poke(false)
      dut.io.debug.burstCounter.expect(1)
      dut.clock.step()
      dut.io.mem.wr.poke(true)
      dut.io.debug.burstCounter.expect(1)
      dut.clock.step()
      dut.io.debug.burstCounter.expect(0)
    }
  }

  it should "hold the burst counter when the wait signal is asserted" in {
    test(mkDDR()) { dut =>
      dut.io.mem.burstLength.poke(2)
      dut.io.mem.wr.poke(true)
      dut.io.ddr.waitReq.poke(true)
      dut.io.debug.burstCounter.expect(0)
      dut.clock.step()
      dut.io.ddr.waitReq.poke(false)
      dut.io.debug.burstCounter.expect(0)
      dut.clock.step()
      dut.io.debug.burstCounter.expect(1)
      dut.clock.step()
      dut.io.debug.burstCounter.expect(0)
    }
  }
}
