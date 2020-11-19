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

package axon.util

import chisel3._
import chiseltest._
import org.scalatest._

class CounterTest extends FlatSpec with ChiselScalatestTester with Matchers {
  it should "increment a static counter" in {
    test(new Module {
      val io = IO(new Bundle {
        val value = Output(UInt())
        val wrap = Output(Bool())
        val enable = Input(Bool())
        val reset = Input(Bool())
      })
      val (value, wrap) = Counter.static(3, enable = io.enable, reset = io.reset)
      io.value := value
      io.wrap := wrap
    }) { dut =>
      dut.io.value.expect(0.U)
      dut.io.wrap.expect(false.B)
      dut.clock.step()
      dut.io.value.expect(0.U)
      dut.io.wrap.expect(false.B)
      dut.io.enable.poke(true.B)
      dut.clock.step()
      dut.io.value.expect(1.U)
      dut.io.wrap.expect(false.B)
      dut.io.reset.poke(true.B)
      dut.clock.step()
      dut.io.reset.poke(false.B)
      dut.io.value.expect(0.U)
      dut.io.wrap.expect(false.B)
      dut.clock.step()
      dut.io.value.expect(1.U)
      dut.io.wrap.expect(false.B)
      dut.clock.step()
      dut.io.value.expect(2.U)
      dut.io.wrap.expect(true.B)
      dut.clock.step()
      dut.io.value.expect(0.U)
      dut.io.wrap.expect(false.B)
    }
  }

  it should "wrap a static counter with zero length" in {
    test(new Module {
      val io = IO(new Bundle {
        val value = Output(UInt())
        val wrap = Output(Bool())
      })
      val (value, wrap) = Counter.static(0)
      io.value := value
      io.wrap := wrap
    }) { dut =>
      dut.io.value.expect(0.U)
      dut.io.wrap.expect(true.B)
      dut.clock.step()
      dut.io.value.expect(0.U)
      dut.io.wrap.expect(true.B)
    }
  }

  it should "increment a dynamic counter" in {
    test(new Module {
      val io = IO(new Bundle {
        val value = Output(UInt())
        val wrap = Output(Bool())
        val enable = Input(Bool())
        val reset = Input(Bool())
      })
      val (value, wrap) = Counter.dynamic(3.U, enable = io.enable, reset = io.reset)
      io.value := value
      io.wrap := wrap
    }) { dut =>
      dut.io.value.expect(0.U)
      dut.io.wrap.expect(false.B)
      dut.clock.step()
      dut.io.value.expect(0.U)
      dut.io.wrap.expect(false.B)
      dut.io.enable.poke(true.B)
      dut.clock.step()
      dut.io.value.expect(1.U)
      dut.io.wrap.expect(false.B)
      dut.io.reset.poke(true.B)
      dut.clock.step()
      dut.io.reset.poke(false.B)
      dut.io.value.expect(0.U)
      dut.io.wrap.expect(false.B)
      dut.clock.step()
      dut.io.value.expect(1.U)
      dut.io.wrap.expect(false.B)
      dut.clock.step()
      dut.io.value.expect(2.U)
      dut.io.wrap.expect(true.B)
      dut.clock.step()
      dut.io.value.expect(0.U)
      dut.io.wrap.expect(false.B)
    }
  }

  it should "wrap a dynamic counter with zero length" in {
    test(new Module {
      val io = IO(new Bundle {
        val value = Output(UInt())
        val wrap = Output(Bool())
      })
      val (value, wrap) = Counter.dynamic(0.U)
      io.value := value
      io.wrap := wrap
    }) { dut =>
      dut.io.value.expect(0.U)
      dut.io.wrap.expect(true.B)
      dut.clock.step()
      dut.io.value.expect(0.U)
      dut.io.wrap.expect(true.B)
    }
  }

}
