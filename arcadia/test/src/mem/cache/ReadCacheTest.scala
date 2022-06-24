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

package arcadia.mem.cache

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

trait ReadCacheTestHelpers {
  protected val cacheConfig = Config(
    inAddrWidth = 16,
    inDataWidth = 8,
    outAddrWidth = 16,
    outDataWidth = 16,
    lineWidth = 2,
    depth = 8
  )

  protected def mkCacheMem(config: Config = cacheConfig) = new ReadCache(config)

  protected def readCache(dut: ReadCache, addr: Int) = {
    dut.io.enable.poke(true)
    waitForIdle(dut)
    dut.io.in.rd.poke(true)
    dut.io.in.addr.poke(addr)
    dut.clock.step()
    dut.io.in.rd.poke(false)
    dut.clock.step()
    val result = dut.io.in.dout.peekInt()
    waitForIdle(dut)
    result
  }

  protected def fillCacheLine(dut: ReadCache, addr: UInt, data: Seq[UInt]): Unit = {
    dut.io.enable.poke(true)
    waitForIdle(dut)
    dut.io.in.rd.poke(true)
    dut.io.in.addr.poke(addr)
    dut.clock.step()
    dut.io.in.rd.poke(false)
    dut.clock.step(2)
    dut.io.out.waitReq.poke(false)

    data.foreach { n =>
      dut.io.out.valid.poke(true)
      dut.io.out.dout.poke(n)
      dut.clock.step()
    }

    dut.io.out.valid.poke(false)
    waitForIdle(dut)
  }

  protected def fillCacheLine(dut: ReadCache, addr: Int, data: Seq[Int]): Unit = {
    fillCacheLine(dut, addr.U, data.map(_.U))
  }

  protected def waitForRequest(dut: ReadCache) = {
    dut.clock.step()
    waitForIdle(dut)
  }

  protected def waitForIdle(dut: ReadCache) =
    while (!dut.io.debug.idle.peekBoolean()) { dut.clock.step() }

  protected def waitForCheck(dut: ReadCache) =
    while (!dut.io.debug.check.peekBoolean()) { dut.clock.step() }

  protected def waitForFill(dut: ReadCache) =
    while (!dut.io.debug.fill.peekBoolean()) { dut.clock.step() }

  protected def waitForFillWait(dut: ReadCache) =
    while (!dut.io.debug.fillWait.peekBoolean()) { dut.clock.step() }

  protected def waitForWrite(dut: ReadCache) =
    while (!dut.io.debug.write.peekBoolean()) { dut.clock.step() }
}

class ReadCacheTest extends AnyFlatSpec with ChiselScalatestTester with Matchers with ReadCacheTestHelpers {
  behavior of "FSM"

  it should "not move to the check state when the cache is disabled" in {
    test(mkCacheMem()) { dut =>
      dut.io.in.rd.poke(true)
      waitForIdle(dut)
      dut.clock.step()
      dut.io.debug.check.expect(false)
    }
  }

  it should "move to the check state after a request" in {
    test(mkCacheMem()) { dut =>
      dut.io.enable.poke(true)
      dut.io.in.rd.poke(true)
      waitForIdle(dut)
      dut.clock.step()
      dut.io.debug.check.expect(true)
    }
  }

  it should "move to the fill state after a read miss" in {
    test(mkCacheMem()) { dut =>
      dut.io.enable.poke(true)
      dut.io.in.rd.poke(true)
      waitForCheck(dut)
      dut.clock.step()
      dut.io.debug.fill.expect(true)
    }
  }

  it should "return to the idle state after writing a cache entry" in {
    test(mkCacheMem()) { dut =>
      dut.io.enable.poke(true)
      dut.io.in.rd.poke(true)
      waitForFill(dut)
      dut.io.in.rd.poke(false)
      dut.io.out.valid.poke(true)
      waitForWrite(dut)
      dut.clock.step()
      dut.io.debug.idle.expect(true)
    }
  }

  it should "return to the idle state after a read hit" in {
    test(mkCacheMem()) { dut =>
      dut.io.enable.poke(true)
      fillCacheLine(dut, 0x00, Seq(0x0001, 0x0002))
      dut.io.in.rd.poke(true)
      waitForCheck(dut)
      dut.io.in.rd.poke(false)
      dut.clock.step()
      dut.io.debug.idle.expect(true)
    }
  }

  behavior of "idle"

  it should "deassert the wait signal" in {
    test(mkCacheMem()) { dut =>
      dut.io.enable.poke(true)
      dut.io.in.waitReq.expect(true)
      waitForIdle(dut)
      dut.io.in.waitReq.expect(false)
      dut.io.enable.poke(false)
      dut.io.in.waitReq.expect(true)
    }
  }

  behavior of "lru"

  it should "toggle the LRU bit during a read hit" in {
    test(mkCacheMem()) { dut =>
      dut.io.enable.poke(true)
      fillCacheLine(dut, 0x00, Seq(0x3412, 0x7856))
      fillCacheLine(dut, 0x04, Seq(0x3412, 0x7856))
      fillCacheLine(dut, 0x20, Seq(0xab90, 0xefcd))
      fillCacheLine(dut, 0x24, Seq(0xab90, 0xefcd))

      // read (tag 0, offset 0)
      dut.io.in.rd.poke(true)
      dut.io.in.addr.poke(0x00)
      dut.io.debug.lru.expect(0)
      waitForRequest(dut)
      dut.io.debug.lru.expect(1)

      // read (tag 1, offset 0)
      dut.io.in.addr.poke(0x20)
      waitForRequest(dut)
      dut.io.debug.lru.expect(0)

      // read (tag 0, offset 1)
      dut.io.in.addr.poke(0x04)
      waitForRequest(dut)
      dut.io.debug.lru.expect(2)

      // read (tag 1, offset 1)
      dut.io.in.addr.poke(0x00)
      waitForRequest(dut)
      dut.io.debug.lru.expect(3)
    }
  }

  behavior of "read"

  it should "read from the cache during a hit" in {
    test(mkCacheMem()) { dut =>
      dut.io.enable.poke(true)
      fillCacheLine(dut, 0x00, Seq(0x3412, 0x7856))

      // read
      dut.io.in.rd.poke(true)
      dut.io.in.addr.poke(0x00)
      dut.clock.step()
      dut.io.out.rd.expect(false)
      dut.clock.step()
      dut.io.in.valid.expect(true)
      dut.io.in.dout.expect(0x12)

      // read
      dut.io.in.addr.poke(0x03)
      dut.clock.step()
      dut.io.out.rd.expect(false)
      dut.clock.step()
      dut.io.in.valid.expect(true)
      dut.io.in.dout.expect(0x78)
    }
  }

  it should "fill a cache line during a miss" in {
    test(mkCacheMem()) { dut =>
      dut.io.enable.poke(true)
      waitForIdle(dut)

      // read
      dut.io.in.rd.poke(true)
      dut.io.in.addr.poke(0x01)
      dut.clock.step()
      dut.io.in.rd.poke(false)
      dut.clock.step()

      // line fill
      dut.io.out.rd.expect(true)
      dut.io.out.burstLength.expect(2)
      dut.io.out.addr.expect(0x00)
      dut.clock.step()
      dut.io.out.valid.poke(true)
      dut.io.out.dout.poke(0x3412)
      dut.io.in.valid.expect(false)
      dut.clock.step()
      dut.io.out.dout.poke(0x7856)
      dut.io.out.rd.expect(false)
      dut.io.in.valid.expect(false)
      dut.clock.step()
      dut.io.out.valid.poke(false)
      dut.io.in.valid.expect(true)
      dut.io.in.dout.expect(0x34)
    }
  }

  it should "fill a cache line during a miss (wide)" in {
    test(mkCacheMem(cacheConfig.copy(inDataWidth = 32))) { dut =>
      dut.io.enable.poke(true)
      waitForIdle(dut)

      // read
      dut.io.in.rd.poke(true)
      dut.io.in.addr.poke(0x00)
      dut.clock.step()
      dut.io.in.rd.poke(false)
      dut.clock.step()

      // line fill
      dut.io.out.rd.expect(true)
      dut.io.out.burstLength.expect(2)
      dut.io.out.addr.expect(0x00)
      dut.clock.step()
      dut.io.out.valid.poke(true)
      dut.io.out.dout.poke(0x3412)
      dut.io.in.valid.expect(false)
      dut.clock.step()
      dut.io.out.dout.poke(0x7856)
      dut.io.in.valid.expect(false)
      dut.io.out.rd.expect(false)
      dut.clock.step()
      dut.io.out.valid.poke(false)
      dut.io.in.valid.expect(true)
      dut.io.in.dout.expect(0x12345678)
    }
  }

  it should "fill a cache line during a miss (wrapping)" in {
    test(mkCacheMem(cacheConfig.copy(wrapping = true))) { dut =>
      dut.io.enable.poke(true)
      waitForIdle(dut)

      // read
      dut.io.in.rd.poke(true)
      dut.io.in.addr.poke(0x03)
      dut.clock.step()
      dut.io.in.rd.poke(false)
      dut.clock.step()

      // line fill
      dut.io.out.rd.expect(true)
      dut.io.out.burstLength.expect(2)
      dut.io.out.addr.expect(0x02)
      dut.clock.step()
      dut.io.out.valid.poke(true)
      dut.io.out.dout.poke(0x3412)
      dut.io.in.valid.expect(false)
      dut.clock.step()
      dut.io.out.dout.poke(0x7856)
      dut.io.out.rd.expect(false)
      dut.io.in.valid.expect(true)
      dut.io.in.dout.expect(0x34)
      dut.clock.step()
      dut.io.out.valid.poke(false)
      dut.io.in.valid.expect(false)
    }
  }

  it should "fill a cache line during a miss (wide wrapping)" in {
    test(mkCacheMem(cacheConfig.copy(inDataWidth = 32, wrapping = true))) { dut =>
      dut.io.enable.poke(true)
      waitForIdle(dut)

      // read
      dut.io.in.rd.poke(true)
      dut.io.in.addr.poke(0x03)
      dut.clock.step()
      dut.io.in.rd.poke(false)
      dut.clock.step()

      // line fill
      dut.io.out.rd.expect(true)
      dut.io.out.burstLength.expect(2)
      dut.io.out.addr.expect(0x02)
      dut.clock.step()
      dut.io.out.valid.poke(true)
      dut.io.out.dout.poke(0x3412)
      dut.io.in.valid.expect(false)
      dut.clock.step()
      dut.io.out.dout.poke(0x7856)
      dut.io.out.rd.expect(false)
      dut.io.in.valid.expect(false)
      dut.clock.step()
      dut.io.out.valid.poke(false)
      dut.io.in.valid.expect(true)
      dut.io.in.dout.expect(0x56781234)
    }
  }

  it should "assert the wait signal during a request" in {
    test(mkCacheMem()) { dut =>
      dut.io.enable.poke(true)
      waitForIdle(dut)
      dut.io.in.rd.poke(true)
      waitForCheck(dut)
      dut.io.in.waitReq.expect(true)
    }
  }

  behavior of "data width ratios"

  it should "read data (8:8)" in {
    test(mkCacheMem(cacheConfig.copy(inDataWidth = 8, outDataWidth = 8))) { dut =>
      dut.io.enable.poke(true)
      fillCacheLine(dut, 0, Seq(0x12, 0x34))
      readCache(dut, 0) shouldBe 0x12
      readCache(dut, 1) shouldBe 0x34
    }
  }

  it should "read data (8:16)" in {
    test(mkCacheMem(cacheConfig.copy(inDataWidth = 8, outDataWidth = 16))) { dut =>
      dut.io.enable.poke(true)
      fillCacheLine(dut, 0, Seq(0x3412, 0x7856))
      readCache(dut, 0) shouldBe 0x12
      readCache(dut, 1) shouldBe 0x34
      readCache(dut, 2) shouldBe 0x56
      readCache(dut, 3) shouldBe 0x78
    }
  }

  it should "read data (8:32)" in {
    test(mkCacheMem(cacheConfig.copy(inDataWidth = 8, outDataWidth = 32))) { dut =>
      dut.io.enable.poke(true)
      fillCacheLine(dut, 0.U, Seq("h_78563412".U, "h_efcdab90".U))
      readCache(dut, 0) shouldBe 0x12
      readCache(dut, 1) shouldBe 0x34
      readCache(dut, 2) shouldBe 0x56
      readCache(dut, 3) shouldBe 0x78
      readCache(dut, 4) shouldBe 0x90
      readCache(dut, 5) shouldBe 0xab
      readCache(dut, 6) shouldBe 0xcd
      readCache(dut, 7) shouldBe 0xef
    }
  }

  it should "read data (16:8)" in {
    test(mkCacheMem(cacheConfig.copy(inDataWidth = 16, outDataWidth = 8))) { dut =>
      dut.io.enable.poke(true)
      fillCacheLine(dut, 0, Seq(0x12, 0x34))
      readCache(dut, 0) shouldBe 0x1234
    }
  }

  it should "read data (16:16)" in {
    test(mkCacheMem(cacheConfig.copy(inDataWidth = 16, outDataWidth = 16))) { dut =>
      dut.io.enable.poke(true)
      fillCacheLine(dut, 0, Seq(0x3412, 0x7856))
      readCache(dut, 0) shouldBe 0x1234
      readCache(dut, 2) shouldBe 0x5678
    }
  }

  it should "read data (16:32)" in {
    test(mkCacheMem(cacheConfig.copy(inDataWidth = 16, outDataWidth = 32, lineWidth = 1))) { dut =>
      dut.io.enable.poke(true)
      fillCacheLine(dut, 0.U, Seq("h_78563412".U))
      readCache(dut, 0) shouldBe 0x1234
      readCache(dut, 2) shouldBe 0x5678
    }
  }
}
