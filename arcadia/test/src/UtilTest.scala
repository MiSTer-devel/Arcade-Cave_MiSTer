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
import chiseltest.experimental.UncheckedClockPoke._
import org.scalatest._
import flatspec.AnyFlatSpec
import matchers.should.Matchers

class UtilTest extends AnyFlatSpec with ChiselScalatestTester with Matchers {
  "rotateLeft" should "rotate the bits to the left" in {
    test(new Module {
      val io = IO(new Bundle {
        val a = Input(UInt(8.W))
        val b = Output(UInt(8.W))
      })
      io.b := Util.rotateLeft(io.a)
    }) { dut =>
      dut.io.a.poke("b1010_0101".U)
      dut.io.b.expect("b0100_1011".U)
    }
  }

  "rotateRight" should "rotate the bits to the right" in {
    test(new Module {
      val io = IO(new Bundle {
        val a = Input(UInt(8.W))
        val b = Output(UInt(8.W))
      })
      io.b := Util.rotateRight(io.a)
    }) { dut =>
      dut.io.a.poke("b1010_0101".U)
      dut.io.b.expect("b1101_0010".U)
    }
  }

  "decode" should "split a bitvector value into a sequence of bitvectors" in {
    test(new Module {
      val io = IO(new Bundle {
        val a = Input(UInt(16.W))
        val b = Output(Vec(4, UInt(4.W)))
      })
      io.b := VecInit(Util.decode(io.a, 4, 4))
    }) { dut =>
      dut.io.a.poke(0x1234)
      dut.io.b(0).expect(4)
      dut.io.b(1).expect(3)
      dut.io.b(2).expect(2)
      dut.io.b(3).expect(1)
    }
  }

  "swapEndianness" should "reverse the byte ordering" in {
    test(new Module {
      val io = IO(new Bundle {
        val a = Input(UInt(32.W))
        val b = Output(UInt(32.W))
      })
      io.b := Util.swapEndianness(io.a)
    }) { dut =>
      dut.io.a.poke(0x12345678)
      dut.io.b.expect(0x78563412)
    }
  }

  "padWords" should "pad words packed into a bitvector" in {
    test(new Module {
      val io = IO(new Bundle {
        val a = Input(UInt(12.W))
        val b = Output(UInt(16.W))
      })
      io.b := Util.padWords(io.a, 4, 3, 4)
    }) { dut =>
      dut.io.a.poke("b001_010_011_100".U)
      dut.io.b.expect("b0001_0010_0011_0100".U)
    }
  }

  "maskBits" should "mask the least significant bits" in {
    test(new Module {
      val io = IO(new Bundle {
        val a = Input(UInt(8.W))
        val b = Output(UInt(8.W))
      })
      io.b := Util.maskBits(io.a, 4)
    }) { dut =>
      dut.io.a.poke(0xff)
      dut.io.b.expect(0xf0)
    }
  }

  "edge" should "detect edges" in {
    test(new Module {
      val io = IO(new Bundle {
        val a = Input(Bool())
        val b = Output(Bool())
      })
      io.b := Util.edge(io.a)
    }) { dut =>
      dut.io.a.poke(false)
      dut.io.b.expect(false)
      dut.clock.step()
      dut.io.a.poke(true)
      dut.io.b.expect(true)
      dut.clock.step()
      dut.io.a.poke(false)
      dut.io.b.expect(true)
    }
  }

  "rising" should "detect rising edges" in {
    test(new Module {
      val io = IO(new Bundle {
        val a = Input(Bool())
        val b = Output(Bool())
      })
      io.b := Util.rising(io.a)
    }) { dut =>
      dut.io.a.poke(false)
      dut.io.b.expect(false)
      dut.clock.step()
      dut.io.a.poke(true)
      dut.io.b.expect(true)
      dut.clock.step()
      dut.io.a.poke(false)
      dut.io.b.expect(false)
    }
  }

  "falling" should "detect falling edges" in {
    test(new Module {
      val io = IO(new Bundle {
        val a = Input(Bool())
        val b = Output(Bool())
      })
      io.b := Util.falling(io.a)
    }) { dut =>
      dut.io.a.poke(true)
      dut.io.b.expect(false)
      dut.clock.step()
      dut.io.a.poke(false)
      dut.io.b.expect(true)
      dut.clock.step()
      dut.io.a.poke(false)
      dut.io.b.expect(false)
    }
  }

  "hold" should "hold a signal" in {
    test(new Module {
      val io = IO(new Bundle {
        val a = Input(UInt(8.W))
        val b = Input(Bool())
        val c = Output(UInt(8.W))
      })
      io.c := Util.hold(io.a, io.b)
    }) { dut =>
      dut.io.a.poke(1)
      dut.io.b.poke(true)
      dut.io.c.expect(1)
      dut.clock.step()
      dut.io.a.poke(0)
      dut.io.c.expect(1)
      dut.clock.step()
      dut.io.c.expect(1)
      dut.io.b.poke(false)
      dut.clock.step()
      dut.io.c.expect(0)
    }
  }

  "latch" should "latch and clear a signal" in {
    test(new Module {
      val io = IO(new Bundle {
        val a = Input(Bool())
        val b = Input(Bool())
        val c = Output(Bool())
      })
      io.c := Util.latch(io.a, io.b)
    }) { dut =>
      dut.io.a.poke(true)
      dut.io.c.expect(true)
      dut.clock.step()
      dut.io.a.poke(false)
      dut.io.c.expect(true)
      dut.clock.step()
      dut.io.b.poke(true)
      dut.io.c.expect(false)
      dut.clock.step()
      dut.io.b.poke(false)
      dut.io.c.expect(false)
    }
  }

  "latchSync" should "latch and clear a signal" in {
    test(new Module {
      val io = IO(new Bundle {
        val a = Input(Bool())
        val b = Input(Bool())
        val c = Output(Bool())
      })
      io.c := Util.latchSync(io.a, io.b)
    }) { dut =>
      dut.io.a.poke(true)
      dut.io.c.expect(false)
      dut.clock.step()
      dut.io.a.poke(false)
      dut.io.c.expect(true)
      dut.clock.step()
      dut.io.b.poke(true)
      dut.io.c.expect(true)
      dut.clock.step()
      dut.io.b.poke(false)
      dut.io.c.expect(false)
    }
  }

  "latchData" should "latch data when the trigger is asserted" in {
    test(new Module {
      val io = IO(new Bundle {
        val a = Input(UInt(8.W))
        val b = Input(Bool())
        val c = Input(Bool())
        val d = Output(UInt(8.W))
      })
      io.d := Util.latchData(io.a, io.b, io.c)
    }) { dut =>
      dut.io.a.poke(1)
      dut.io.b.poke(true)
      dut.io.d.expect(1)
      dut.clock.step()
      dut.io.a.poke(2)
      dut.io.b.poke(false)
      dut.io.d.expect(1)
      dut.clock.step()
      dut.io.c.poke(true)
      dut.io.d.expect(2)
      dut.clock.step()
      dut.io.c.poke(false)
      dut.io.d.expect(2)
    }
  }

  "pulseSync" should "trigger a pulse" in {
    test(new Module {
      val io = IO(new Bundle {
        val a = Input(Bool())
        val b = Output(Bool())
      })
      io.b := Util.pulseSync(2, io.a)
    }) { dut =>
      dut.io.a.poke(true)
      dut.io.b.expect(false)
      dut.clock.step()
      dut.io.b.expect(true)
      dut.clock.step()
      dut.io.b.expect(true)
      dut.clock.step()
      dut.io.b.expect(false)
      dut.clock.step()
      dut.io.a.poke(false)
    }
  }

  "toggle" should "toggle a bit" in {
    test(new Module {
      val io = IO(new Bundle {
        val a = Input(Bool())
        val b = Output(Bool())
      })
      io.b := Util.toggle(io.a)
    }) { dut =>
      dut.io.b.expect(false)
      dut.clock.step()
      dut.io.a.poke(true)
      dut.io.b.expect(false)
      dut.clock.step()
      dut.io.b.expect(true)
      dut.clock.step()
      dut.io.b.expect(false)
    }
  }

  "sync" should "generate a sync pulse for rising edges of the target clock" ignore  {
    test(new Module {
      val io = IO(new Bundle {
        val a = Input(Clock())
        val b = Output(Bool())
      })
      io.b := Util.sync(io.a)
    }) { dut =>
      dut.io.a.low()
      dut.io.b.expect(false)
      dut.clock.step()
      dut.io.a.high()
      dut.io.b.expect(true)
      dut.clock.step()
      dut.io.a.low()
      dut.io.b.expect(false)
    }
  }
}
