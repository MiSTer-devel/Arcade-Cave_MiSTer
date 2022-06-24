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

package arcadia.clk

import chisel3._
import chiseltest._
import org.scalatest._
import flatspec.AnyFlatSpec
import matchers.should.Matchers

class ClockDividerTest extends AnyFlatSpec with ChiselScalatestTester with Matchers {
  it should "divide the clock by 2" in {
    test(new Module {
      val io = IO(new Bundle {
        val clockEnable = Output(Bool())
      })
      io.clockEnable := ClockDivider(2)
    }) { dut =>
      dut.io.clockEnable.expect(false)
      dut.clock.step()
      dut.io.clockEnable.expect(true)
      dut.clock.step()
      dut.io.clockEnable.expect(false)
      dut.clock.step()
      dut.io.clockEnable.expect(true)
    }
  }

  it should "divide the clock by 3" in {
    test(new Module {
      val io = IO(new Bundle {
        val clockEnable = Output(Bool())
      })
      io.clockEnable := ClockDivider(3)
    }) { dut =>
      dut.io.clockEnable.expect(false)
      dut.clock.step()
      dut.io.clockEnable.expect(false)
      dut.clock.step()
      dut.io.clockEnable.expect(false)
      dut.clock.step()
      dut.io.clockEnable.expect(true)
      dut.clock.step()
      dut.io.clockEnable.expect(false)
      dut.clock.step()
      dut.io.clockEnable.expect(false)
      dut.clock.step()
      dut.io.clockEnable.expect(true)
    }
  }
}
