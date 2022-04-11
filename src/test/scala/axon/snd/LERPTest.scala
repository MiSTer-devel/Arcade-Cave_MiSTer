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

package axon.snd

import chisel3._
import chiseltest._
import org.scalatest._
import flatspec.AnyFlatSpec
import matchers.should.Matchers

class LERPTest extends AnyFlatSpec with ChiselScalatestTester with Matchers {
  it should "interpolate sample values" in {
    test(new LERP) { dut =>
      dut.io.samples(0).poke(0)
      dut.io.samples(1).poke(16)

      dut.io.index.poke(0)
      dut.io.out.expect(0.S)

      dut.io.index.poke(64)
      dut.io.out.expect(4.S)

      dut.io.index.poke(128)
      dut.io.out.expect(8.S)

      dut.io.index.poke(192)
      dut.io.out.expect(12.S)

      dut.io.index.poke(256)
      dut.io.out.expect(16.S)
    }
  }

  it should "handle min/max sample values" in {
    test(new LERP) { dut =>
      dut.io.samples(0).poke(-32767)
      dut.io.samples(1).poke(32767)

      dut.io.index.poke(0)
      dut.io.out.expect(-32767.S)

      dut.io.index.poke(128)
      dut.io.out.expect(0.S)

      dut.io.index.poke(256)
      dut.io.out.expect(32767.S)
    }
  }
}
