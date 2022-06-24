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

package arcadia.mem.arbiter

import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class AsyncMemArbiterTest extends AnyFlatSpec with ChiselScalatestTester with Matchers {
  private def mkMemArbiter = new AsyncMemArbiter(2, 8, 8)

  it should "mux the request to the input port with the highest priority" in {
    test(mkMemArbiter) { dut =>
      // read 0
      dut.io.in(0).rd.poke(true)
      dut.io.in(0).addr.poke(1)
      dut.io.in(1).wr.poke(true)
      dut.io.in(1).addr.poke(2)
      dut.io.out.rd.expect(true)
      dut.io.out.wr.expect(false)
      dut.io.out.addr.expect(1)
      dut.io.chosen.expect(1)

      // write 1
      dut.io.in(0).rd.poke(false)
      dut.io.out.rd.expect(false)
      dut.io.out.wr.expect(true)
      dut.io.out.addr.expect(2)
      dut.io.chosen.expect(2)

      // done
      dut.io.in(1).wr.poke(false)
      dut.io.out.rd.expect(false)
      dut.io.out.wr.expect(false)
      dut.io.out.addr.expect(0)
      dut.io.chosen.expect(0)
    }
  }

  it should "assert the wait signal when the output is busy" in {
    test(mkMemArbiter) { dut =>
      dut.io.out.waitReq.poke(true)

      // read 0+1
      dut.io.in(0).rd.poke(true)
      dut.io.in(1).rd.poke(true)
      dut.io.in(0).waitReq.expect(true)
      dut.io.in(1).waitReq.expect(true)

      // wait 0
      dut.io.out.waitReq.poke(false)
      dut.io.in(0).waitReq.expect(false)
      dut.io.in(1).waitReq.expect(true)
      dut.io.out.waitReq.poke(true)
      dut.io.in(0).waitReq.expect(true)
      dut.io.in(1).waitReq.expect(true)

      // read 1
      dut.io.in(0).rd.poke(false)
      dut.io.in(1).rd.poke(true)
      dut.io.in(0).waitReq.expect(true)
      dut.io.in(1).waitReq.expect(true)

      // wait 1
      dut.io.out.waitReq.poke(false)
      dut.io.in(0).waitReq.expect(true)
      dut.io.in(1).waitReq.expect(false)
      dut.io.out.waitReq.poke(true)
      dut.io.in(0).waitReq.expect(true)
      dut.io.in(1).waitReq.expect(true)
    }
  }

  it should "assert not the wait signal when there are no requests" in {
    test(mkMemArbiter) { dut =>
      dut.io.in(0).waitReq.expect(false)
      dut.io.in(1).waitReq.expect(false)
      dut.io.in(0).rd.poke(true)
      dut.io.in(0).waitReq.expect(false)
      dut.io.in(1).waitReq.expect(true)
    }
  }

  it should "assert the valid signal" in {
    test(mkMemArbiter) { dut =>
      // read 0+1
      dut.io.in(0).rd.poke(true)
      dut.io.in(1).rd.poke(true)
      dut.clock.step()

      // valid 0
      dut.io.out.valid.poke(true)
      dut.io.out.dout.poke(1)
      dut.io.in(0).valid.expect(true)
      dut.io.in(1).valid.expect(false)
      dut.io.in(0).dout.expect(1)
      dut.clock.step()

      // valid 1
      dut.io.out.dout.poke(2)
      dut.io.in(0).valid.expect(true)
      dut.io.in(1).valid.expect(false)
      dut.io.in(0).dout.expect(2)
      dut.clock.step()
      dut.io.out.valid.poke(false)
      dut.io.in(0).rd.poke(false)
      dut.clock.step()

      // valid 2
      dut.io.in(1).rd.poke(false)
      dut.io.out.valid.poke(true)
      dut.io.out.dout.poke(3)
      dut.io.in(0).valid.expect(false)
      dut.io.in(1).valid.expect(true)
      dut.io.in(1).dout.expect(3)
    }
  }

  it should "assert the busy signal for pending requests" in {
    test(mkMemArbiter) { dut =>
      dut.io.in(0).rd.poke(true)
      dut.io.busy.expect(false)
      dut.clock.step()
      dut.io.in(0).rd.poke(false)
      dut.io.out.valid.poke(true)
      dut.io.busy.expect(true)
      dut.clock.step()
      dut.io.busy.expect(false)
    }
  }

  it should "not assert the busy signal for requests that are always valid" in {
    test(mkMemArbiter) { dut =>
      dut.io.in(0).rd.poke(true)
      dut.io.out.valid.poke(true)
      dut.io.busy.expect(false)
      dut.clock.step()
      dut.io.busy.expect(false)
    }
  }

  it should "not assert the busy signal for write requests" in {
    test(mkMemArbiter) { dut =>
      dut.io.in(0).wr.poke(true)
      dut.io.busy.expect(false)
      dut.clock.step()
      dut.io.busy.expect(false)
    }
  }
}
