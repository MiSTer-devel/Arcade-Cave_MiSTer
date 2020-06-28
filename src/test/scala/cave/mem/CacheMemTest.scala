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
 * Copyright (c) 2020 Josh Bassett
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package cave.mem

import chisel3._
import chiseltest._
import org.scalatest._

class CacheMemTest extends FlatSpec with ChiselScalatestTester with Matchers {
  behavior of "a cache miss"

  it should "read a cache line from memory" in {
    test(new CacheMem(addrWidth = 8, dataWidthIn = 8, dataWidthOut = 4)) { c =>
      c.io.cpu.addr.poke(0.U)
      c.io.cpu.ready.poke(true.B)

      c.clock.step()

      c.io.mem.addr.expect(0.U)
      c.io.mem.ready.expect(true.B)
      c.io.cpu.valid.expect(false.B)

      c.clock.step()

      c.io.mem.bits.poke("b0010_0001".U)
      c.io.mem.valid.poke(true.B)
      c.io.cpu.valid.expect(false.B)

      c.clock.step()

      c.io.mem.ready.expect(false.B)
      c.io.cpu.bits.expect("b0001".U)
      c.io.cpu.valid.expect(true.B)

      c.clock.step()

      c.io.cpu.addr.poke(2.U)
      c.io.cpu.ready.poke(true.B)

      c.clock.step()

      c.io.mem.addr.expect(2.U)
      c.io.mem.ready.expect(true.B)
      c.io.cpu.valid.expect(false.B)

      c.clock.step()

      c.io.mem.bits.poke("b1000_0100".U)
      c.io.mem.valid.poke(true.B)
      c.io.cpu.valid.expect(false.B)

      c.clock.step()

      c.io.mem.ready.expect(false.B)
      c.io.cpu.bits.expect("b0100".U)
      c.io.cpu.valid.expect(true.B)
    }
  }

  behavior of "a cache hit"

  it should "not read a cache line from memory" in {
    test(new CacheMem(addrWidth = 8, dataWidthIn = 8, dataWidthOut = 4)) { c =>
      c.io.cpu.addr.poke(0.U)
      c.io.cpu.ready.poke(true.B)

      c.clock.step()

      c.io.mem.addr.expect(0.U)
      c.io.mem.ready.expect(true.B)
      c.io.cpu.valid.expect(false.B)

      c.clock.step()

      c.io.mem.bits.poke("b0010_0001".U)
      c.io.mem.valid.poke(true.B)
      c.io.cpu.valid.expect(false.B)

      c.clock.step()

      c.io.mem.ready.expect(false.B)
      c.io.cpu.bits.expect("b0001".U)
      c.io.cpu.valid.expect(true.B)

      c.clock.step()

      c.io.cpu.addr.poke(1.U)
      c.io.cpu.ready.poke(true.B)

      c.clock.step()

      c.io.mem.ready.expect(false.B)
      c.io.cpu.bits.expect("b0010".U)
      c.io.cpu.valid.expect(true.B)
    }
  }
}
