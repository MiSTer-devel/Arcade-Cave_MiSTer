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
import org.scalatest._
import flatspec.AnyFlatSpec
import matchers.should.Matchers

class Vec2Test extends AnyFlatSpec with ChiselScalatestTester with Matchers {
  behavior of "unsigned vector"

  it should "create a new vector" in {
    test(new Module {
      val io = IO(new Bundle {
        val a = Output(UVec2(4.W))
      })
      io.a := UVec2(1.U, 2.U)
    }) { dut =>
      dut.io.a.x.expect(1)
      dut.io.a.y.expect(2)
    }
  }

  it should "add two vectors" in {
    test(new Module {
      val io = IO(new Bundle {
        val a = Input(UVec2(4.W))
        val b = Input(UVec2(4.W))
        val c = Output(UVec2(4.W))
      })
      io.c := io.a + io.b
    }) { dut =>
      dut.io.a.x.poke(1)
      dut.io.a.y.poke(2)
      dut.io.b.x.poke(3)
      dut.io.b.y.poke(4)
      dut.io.c.x.expect(4)
      dut.io.c.y.expect(6)
    }
  }

  it should "subtract two vectors" in {
    test(new Module {
      val io = IO(new Bundle {
        val a = Input(UVec2(4.W))
        val b = Input(UVec2(4.W))
        val c = Output(UVec2(4.W))
      })
      io.c := io.a - io.b
    }) { dut =>
      dut.io.a.x.poke(4)
      dut.io.a.y.poke(3)
      dut.io.b.x.poke(2)
      dut.io.b.y.poke(1)
      dut.io.c.x.expect(2)
      dut.io.c.y.expect(2)
    }
  }

  it should "multiply by scalar" in {
    test(new Module {
      val io = IO(new Bundle {
        val a = Input(UVec2(4.W))
        val b = Output(UVec2(4.W))
      })
      io.b := io.a * 2.U
    }) { dut =>
      dut.io.a.x.poke(1)
      dut.io.a.y.poke(2)
      dut.io.b.x.expect(2)
      dut.io.b.y.expect(4)
    }
  }

  it should "shift left" in {
    test(new Module {
      val io = IO(new Bundle {
        val a = Input(UVec2(4.W))
        val b = Output(UVec2(4.W))
      })
      io.b := io.a << 1
    }) { dut =>
      dut.io.a.x.poke(1)
      dut.io.a.y.poke(2)
      dut.io.b.x.expect(2)
      dut.io.b.y.expect(4)
    }
  }

  it should "shift right" in {
    test(new Module {
      val io = IO(new Bundle {
        val a = Input(UVec2(4.W))
        val b = Output(UVec2(4.W))
      })
      io.b := io.a >> 1
    }) { dut =>
      dut.io.a.x.poke(2)
      dut.io.a.y.poke(4)
      dut.io.b.x.expect(1)
      dut.io.b.y.expect(2)
    }
  }

  behavior of "signed vector"

  it should "create a new vector" in {
    test(new Module {
      val io = IO(new Bundle {
        val a = Output(SVec2(4.W))
      })
      io.a := SVec2(1.S, 2.S)
    }) { dut =>
      dut.io.a.x.expect(1)
      dut.io.a.y.expect(2)
    }
  }

  it should "add two vectors" in {
    test(new Module {
      val io = IO(new Bundle {
        val a = Input(SVec2(4.W))
        val b = Input(SVec2(4.W))
        val c = Output(SVec2(4.W))
      })
      io.c := io.a + io.b
    }) { dut =>
      dut.io.a.x.poke(1)
      dut.io.a.y.poke(2)
      dut.io.b.x.poke(3)
      dut.io.b.y.poke(4)
      dut.io.c.x.expect(4)
      dut.io.c.y.expect(6)
    }
  }

  it should "subtract two vectors" in {
    test(new Module {
      val io = IO(new Bundle {
        val a = Input(SVec2(4.W))
        val b = Input(SVec2(4.W))
        val c = Output(SVec2(4.W))
      })
      io.c := io.a - io.b
    }) { dut =>
      dut.io.a.x.poke(4)
      dut.io.a.y.poke(3)
      dut.io.b.x.poke(2)
      dut.io.b.y.poke(1)
      dut.io.c.x.expect(2)
      dut.io.c.y.expect(2)
    }
  }

  it should "multiply by scalar" in {
    test(new Module {
      val io = IO(new Bundle {
        val a = Input(SVec2(4.W))
        val b = Output(SVec2(4.W))
      })
      io.b := io.a * 2.S
    }) { dut =>
      dut.io.a.x.poke(1)
      dut.io.a.y.poke(2)
      dut.io.b.x.expect(2)
      dut.io.b.y.expect(4)
    }
  }

  it should "shift left" in {
    test(new Module {
      val io = IO(new Bundle {
        val a = Input(SVec2(4.W))
        val b = Output(SVec2(4.W))
      })
      io.b := io.a << 1
    }) { dut =>
      dut.io.a.x.poke(1)
      dut.io.a.y.poke(2)
      dut.io.b.x.expect(2)
      dut.io.b.y.expect(4)
    }
  }

  it should "shift right" in {
    test(new Module {
      val io = IO(new Bundle {
        val a = Input(SVec2(4.W))
        val b = Output(SVec2(4.W))
      })
      io.b := io.a >> 1
    }) { dut =>
      dut.io.a.x.poke(2)
      dut.io.a.y.poke(4)
      dut.io.b.x.expect(1)
      dut.io.b.y.expect(2)
    }
  }
}
