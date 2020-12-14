/*
 *    __   __     __  __     __         __
 *   /\ "-.\ \   /\ \/\ \   /\ \       /\ \
 *   \ \ \-.  \  \ \ \_\ \  \ \ \____  \ \ \____
 *    \ \_\\"\_\  \ \_____\  \ \_____\  \ \_____\
 *     \/_/ \/_/   \/_____/   \/_____/   \/_____/
 *    ______     ______       __     ______     ______     ______
 *   /\  __ \   /\  == \     /\ \   /\  ___\   /\  ___\   /\__  _\
 *   \ \ \/\ \  \ \  __<    _\_\ \  \ \  __\   \ \ \____  \/_/\ \/
 *    \ \_____\  \ \_____\ /\_____\  \ \_____\  \ \_____\    \ \_\
 *     \/_____/   \/_____/ \/_____/   \/_____/   \/_____/     \/_/
 *
 *  https://joshbassett.info
 *  https://twitter.com/nullobject
 *  https://github.com/nullobject
 *
 *  Copyright (c) 2020 Josh Bassett
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
 */

package axon

import chisel3._
import chiseltest._
import chiseltest.experimental.UncheckedClockPoke._
import org.scalatest._

class UtilTest extends FlatSpec with ChiselScalatestTester with Matchers {
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
      dut.io.a.poke(0x1234.U)
      dut.io.b(3).expect(1.U)
      dut.io.b(2).expect(2.U)
      dut.io.b(1).expect(3.U)
      dut.io.b(0).expect(4.U)
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

  "edge" should "detect edges" in {
    test(new Module {
      val io = IO(new Bundle {
        val a = Input(Bool())
        val b = Output(Bool())
      })
      io.b := Util.edge(io.a)
    }) { dut =>
      dut.io.a.poke(false.B)
      dut.io.b.expect(false.B)
      dut.clock.step()
      dut.io.a.poke(true.B)
      dut.io.b.expect(true.B)
      dut.clock.step()
      dut.io.a.poke(false.B)
      dut.io.b.expect(true.B)
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
      dut.io.a.poke(false.B)
      dut.io.b.expect(false.B)
      dut.clock.step()
      dut.io.a.poke(true.B)
      dut.io.b.expect(true.B)
      dut.clock.step()
      dut.io.a.poke(false.B)
      dut.io.b.expect(false.B)
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
      dut.io.a.poke(true.B)
      dut.io.b.expect(false.B)
      dut.clock.step()
      dut.io.a.poke(false.B)
      dut.io.b.expect(true.B)
      dut.clock.step()
      dut.io.a.poke(false.B)
      dut.io.b.expect(false.B)
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
      dut.io.a.poke(1.U)
      dut.io.b.poke(true.B)
      dut.io.c.expect(1.U)
      dut.clock.step()
      dut.io.a.poke(0.U)
      dut.io.c.expect(1.U)
      dut.clock.step()
      dut.io.c.expect(1.U)
      dut.io.b.poke(false.B)
      dut.clock.step()
      dut.io.c.expect(0.U)
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
      dut.io.a.poke(true.B)
      dut.io.c.expect(true.B)
      dut.clock.step()
      dut.io.a.poke(false.B)
      dut.io.c.expect(true.B)
      dut.clock.step()
      dut.io.b.poke(true.B)
      dut.io.c.expect(false.B)
      dut.clock.step()
      dut.io.b.poke(false.B)
      dut.io.c.expect(false.B)
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
      dut.io.a.poke(true.B)
      dut.io.c.expect(false.B)
      dut.clock.step()
      dut.io.a.poke(false.B)
      dut.io.c.expect(true.B)
      dut.clock.step()
      dut.io.b.poke(true.B)
      dut.io.c.expect(true.B)
      dut.clock.step()
      dut.io.b.poke(false.B)
      dut.io.c.expect(false.B)
    }
  }

  it should "latch a signal when the trigger is asserted" in {
    test(new Module {
      val io = IO(new Bundle {
        val a = Input(UInt(8.W))
        val b = Input(Bool())
        val c = Input(Bool())
        val d = Output(UInt(8.W))
      })
      io.d := Util.latch(io.a, io.b, io.c)
    }) { dut =>
      dut.io.a.poke(1.U)
      dut.io.b.poke(true.B)
      dut.io.d.expect(1.U)
      dut.clock.step()
      dut.io.a.poke(2.U)
      dut.io.b.poke(false.B)
      dut.io.d.expect(1.U)
      dut.clock.step()
      dut.io.c.poke(true.B)
      dut.io.d.expect(2.U)
      dut.clock.step()
      dut.io.c.poke(false.B)
      dut.io.d.expect(2.U)
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
      dut.io.b.expect(false.B)
      dut.clock.step()
      dut.io.a.poke(true.B)
      dut.io.b.expect(false.B)
      dut.clock.step()
      dut.io.b.expect(true.B)
      dut.clock.step()
      dut.io.b.expect(false.B)
    }
  }

  "sync" should "generate a sync pulse for rising edges of the target clock" in {
    test(new Module {
      val io = IO(new Bundle {
        val a = Input(Clock())
        val b = Output(Bool())
      })
      io.b := Util.sync(io.a)
    }) { dut =>
      dut.io.a.low()
      dut.io.b.expect(false.B)
      dut.clock.step()
      dut.io.a.high()
      dut.io.b.expect(true.B)
      dut.clock.step()
      dut.io.a.low()
      dut.io.b.expect(false.B)
    }
  }
}
