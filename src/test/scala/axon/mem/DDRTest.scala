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

trait DDRTestHelpers {
  protected val ddrConfig = DDRConfig(addrWidth = 16, dataWidth = 16)

  protected def mkDDR(config: DDRConfig = ddrConfig) = new DDR(config)
}

class DDRTest extends FlatSpec with ChiselScalatestTester with Matchers with DDRTestHelpers {
  behavior of "read"

  it should "read from DDR memory" in {
    test(mkDDR()) { dut =>
      // Request
      dut.io.mem.burstLength.poke(2.U)
      dut.io.mem.rd.poke(true.B)
      dut.io.mem.addr.poke(1.U)
      dut.io.ddr.rd.expect(true.B)
      dut.io.ddr.addr.expect(1.U)
      dut.clock.step()
      dut.io.ddr.rd.expect(false.B)

      // Data
      dut.io.mem.rd.poke(false.B)
      dut.io.ddr.valid.poke(true.B)
      dut.io.ddr.dout.poke(0x1234.U)
      dut.io.mem.valid.expect(true.B)
      dut.io.mem.dout.expect(0x1234.U)
      dut.clock.step()
      dut.io.ddr.dout.poke(0x5678.U)
      dut.io.mem.burstDone.expect(true.B)
      dut.io.mem.valid.expect(true.B)
      dut.io.mem.dout.expect(0x5678.U)
      dut.clock.step()

      // Done
      dut.io.mem.burstDone.expect(false.B)
      dut.io.mem.rd.expect(false.B)
    }
  }

  it should "latch the burst length" in {
    test(mkDDR()) { dut =>
      dut.io.mem.burstLength.poke(2.U)
      dut.io.mem.rd.poke(true.B)
      dut.io.ddr.burstLength.expect(2.U)
      dut.clock.step()
      dut.io.mem.burstLength.poke(1.U)
      dut.io.ddr.valid.poke(true.B)
      dut.io.ddr.burstLength.expect(2.U)
      dut.clock.step(2)
      dut.io.ddr.burstLength.expect(1.U)
    }
  }

  it should "hold the burst counter when the wait signal is asserted" in {
    test(mkDDR()) { dut =>
      dut.io.mem.burstLength.poke(2.U)
      dut.io.mem.rd.poke(true.B)
      dut.io.ddr.waitReq.poke(true.B)
      dut.io.debug.burstCounter.expect(0.U)
      dut.clock.step()
      dut.io.ddr.waitReq.poke(false.B)
      dut.io.ddr.valid.poke(true.B)
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

  it should "write to DDR memory" in {
    test(mkDDR()) { dut =>
      // Request
      dut.io.mem.burstLength.poke(2.U)
      dut.io.mem.wr.poke(true.B)
      dut.io.mem.addr.poke(1.U)
      dut.io.mem.din.poke(0x1234.U)
      dut.io.ddr.wr.expect(true.B)
      dut.io.ddr.addr.expect(1.U)
      dut.io.ddr.din.expect(0x1234.U)
      dut.clock.step()

      // Data
      dut.io.mem.wr.poke(false.B)
      dut.io.mem.din.poke(0x5678.U)
      dut.io.ddr.wr.expect(true.B)
      dut.io.ddr.din.expect(0x5678.U)
      dut.io.mem.burstDone.expect(true.B)
      dut.clock.step()

      // Done
      dut.io.mem.burstDone.expect(false.B)
      dut.io.ddr.wr.expect(false.B)
    }
  }

  it should "latch the burst length" in {
    test(mkDDR()) { dut =>
      dut.io.mem.burstLength.poke(2.U)
      dut.io.mem.wr.poke(true.B)
      dut.io.ddr.burstLength.expect(2.U)
      dut.clock.step()
      dut.io.mem.burstLength.poke(1.U)
      dut.io.ddr.burstLength.expect(2.U)
      dut.clock.step(2)
      dut.io.ddr.burstLength.expect(1.U)
    }
  }

  it should "hold the burst counter when the wait signal is asserted" in {
    test(mkDDR()) { dut =>
      dut.io.mem.burstLength.poke(2.U)
      dut.io.mem.wr.poke(true.B)
      dut.io.ddr.waitReq.poke(true.B)
      dut.io.debug.burstCounter.expect(0.U)
      dut.clock.step()
      dut.io.ddr.waitReq.poke(false.B)
      dut.io.debug.burstCounter.expect(0.U)
      dut.clock.step()
      dut.io.debug.burstCounter.expect(1.U)
      dut.clock.step()
      dut.io.debug.burstCounter.expect(0.U)
    }
  }
}
