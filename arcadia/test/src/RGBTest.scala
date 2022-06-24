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

package arcadia

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class RGBTest extends AnyFlatSpec with ChiselScalatestTester with Matchers {
  it should "create a new RGB" in {
    test(new Module {
      val io = IO(new Bundle {
        val a = Output(RGB(4.W))
      })
      io.a := RGB(1.U, 2.U, 3.U)
    }) { dut =>
      dut.io.a.r.expect(1)
      dut.io.a.g.expect(2)
      dut.io.a.b.expect(3)
    }
  }

  it should "AND two RGB values" in {
    test(new Module {
      val io = IO(new Bundle {
        val a = Input(RGB(4.W))
        val b = Input(RGB(4.W))
        val c = Output(RGB(4.W))
      })
      io.c := io.a & io.b
    }) { dut =>
      dut.io.a.r.poke(1)
      dut.io.a.g.poke(2)
      dut.io.a.b.poke(4)
      dut.io.b.r.poke(1)
      dut.io.b.g.poke(2)
      dut.io.b.b.poke(5)
      dut.io.c.r.expect(1)
      dut.io.c.g.expect(2)
      dut.io.c.b.expect(4)
    }
  }

  it should "OR two RGB values" in {
    test(new Module {
      val io = IO(new Bundle {
        val a = Input(RGB(4.W))
        val b = Input(RGB(4.W))
        val c = Output(RGB(4.W))
      })
      io.c := io.a | io.b
    }) { dut =>
      dut.io.a.r.poke(1)
      dut.io.a.g.poke(2)
      dut.io.a.b.poke(4)
      dut.io.b.r.poke(1)
      dut.io.b.g.poke(2)
      dut.io.b.b.poke(5)
      dut.io.c.r.expect(1)
      dut.io.c.g.expect(2)
      dut.io.c.b.expect(5)
    }
  }
}
