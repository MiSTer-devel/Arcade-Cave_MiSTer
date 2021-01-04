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

package axon.util

import chisel3._
import chiseltest._
import org.scalatest._

class PISOTest extends FlatSpec with ChiselScalatestTester with Matchers {
  def mkPISO = new Module {
    val io = IO(new Bundle {
      val wr = Input(Bool())
      val isEmpty = Output(Bool())
      val isAlmostEmpty = Output(Bool())
      val din = Input(Vec(3, UInt(8.W)))
      val dout = Output(UInt(8.W))
    })
    val piso = Module(new PISO(UInt(8.W), 3))
    piso.io.wr := io.wr
    io.isEmpty := piso.io.isEmpty
    io.isAlmostEmpty := piso.io.isAlmostEmpty
    piso.io.din := io.din
    io.dout := piso.io.dout
  }

  it should "latch parallel values and emit them serially" in {
    test(mkPISO) { dut =>
      dut.io.din(0).poke(1.U)
      dut.io.din(1).poke(2.U)
      dut.io.din(2).poke(3.U)
      dut.io.wr.poke(true.B)
      dut.clock.step()
      dut.io.wr.poke(false.B)
      dut.io.dout.expect(1.U)
      dut.clock.step()
      dut.io.dout.expect(2.U)
      dut.clock.step()
      dut.io.dout.expect(3.U)
    }
  }

  it should "assert the isEmpty signal" in {
    test(mkPISO) { dut =>
      dut.io.isEmpty.expect(true.B)
      dut.io.wr.poke(true.B)
      dut.clock.step()
      dut.io.wr.poke(false.B)
      dut.io.isEmpty.expect(false.B)
      dut.clock.step(3)
      dut.io.isEmpty.expect(true.B)
    }
  }

  it should "assert the isAlmostEmpty signal" in {
    test(mkPISO) { dut =>
      dut.io.isAlmostEmpty.expect(false.B)
      dut.io.wr.poke(true.B)
      dut.clock.step()
      dut.io.wr.poke(false.B)
      dut.io.isAlmostEmpty.expect(false.B)
      dut.clock.step(2)
      dut.io.isAlmostEmpty.expect(true.B)
      dut.clock.step()
      dut.io.isAlmostEmpty.expect(false.B)
    }
  }
}
