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

package axon.mem

import chisel3._
import chiseltest._
import org.scalatest._

trait CacheMemTestHelpers {
  protected val cacheConfig = CacheConfig(
    inAddrWidth = 16,
    inDataWidth = 8,
    outAddrWidth = 16,
    outDataWidth = 16,
    lineWidth = 2,
    depth = 8,
    offset = 0x1000
  )

  protected def mkCacheMem(config: CacheConfig = cacheConfig) = new Cache(config)

  protected def readCache(dut: Cache, addr: Int) = {
    dut.io.in.rd.poke(true.B)
    dut.io.in.wr.poke(false.B)
    dut.io.in.addr.poke(addr.U)
    dut.clock.step()
    dut.io.in.rd.poke(false.B)
    dut.clock.step()
    val result = dut.io.in.dout.peek().litValue()
    waitForIdle(dut)
    result
  }

  protected def writeCache(dut: Cache, addr: Int, data: Int) = {
    dut.io.in.rd.poke(false.B)
    dut.io.in.wr.poke(true.B)
    dut.io.in.addr.poke(addr.U)
    dut.io.in.din.poke(data.U)
    dut.clock.step()
    dut.io.in.wr.poke(false.B)
    waitForIdle(dut)
  }

  protected def fillCacheLine(dut: Cache, addr: Int, data: Seq[Int]) = {
    dut.io.in.rd.poke(true.B)
    dut.io.in.wr.poke(false.B)
    dut.io.in.addr.poke(addr.U)
    dut.clock.step()
    dut.io.in.rd.poke(false.B)
    dut.clock.step(2)
    dut.io.out.waitReq.poke(false.B)
    dut.clock.step()

    data.foreach { n =>
      dut.io.out.valid.poke(true.B)
      dut.io.out.dout.poke(n.U)
      dut.clock.step()
    }

    dut.io.out.valid.poke(false.B)
    waitForIdle(dut)
  }

  protected def waitForIdle(dut: Cache) =
    while (!dut.io.debug.idle.peek().litToBoolean) { dut.clock.step() }

  protected def waitForLatch(dut: Cache) =
    while (!dut.io.debug.latch.peek().litToBoolean) { dut.clock.step() }

  protected def waitForCheck(dut: Cache) =
    while (!dut.io.debug.check.peek().litToBoolean) { dut.clock.step() }

  protected def waitForFill(dut: Cache) =
    while (!dut.io.debug.fill.peek().litToBoolean) { dut.clock.step() }

  protected def waitForFillWait(dut: Cache) =
    while (!dut.io.debug.fillWait.peek().litToBoolean) { dut.clock.step() }

  protected def waitForEvict(dut: Cache) =
    while (!dut.io.debug.evict.peek().litToBoolean) { dut.clock.step() }

  protected def waitForEvictWait(dut: Cache) =
    while (!dut.io.debug.evictWait.peek().litToBoolean) { dut.clock.step() }

  protected def waitForMerge(dut: Cache) =
    while (!dut.io.debug.merge.peek().litToBoolean) { dut.clock.step() }

  protected def waitForWrite(dut: Cache) =
    while (!dut.io.debug.write.peek().litToBoolean) { dut.clock.step() }
}

class CacheTest extends FlatSpec with ChiselScalatestTester with Matchers with CacheMemTestHelpers {
  behavior of "FSM"

  it should "move to the latch state after a request" in {
    test(mkCacheMem()) { dut =>
      dut.io.in.rd.poke(true.B)
      waitForIdle(dut)
      dut.clock.step()
      dut.io.debug.latch.expect(true.B)
    }
  }

  it should "move to the check state after latching a cache entry" in {
    test(mkCacheMem()) { dut =>
      dut.io.in.rd.poke(true.B)
      waitForLatch(dut)
      dut.clock.step()
      dut.io.debug.check.expect(true.B)
    }
  }

  it should "move to the fill state after a read miss" in {
    test(mkCacheMem()) { dut =>
      dut.io.in.rd.poke(true.B)
      waitForCheck(dut)
      dut.clock.step()
      dut.io.debug.fill.expect(true.B)
    }
  }

  it should "move to the fill state after a write miss" in {
    test(mkCacheMem()) { dut =>
      dut.io.in.wr.poke(true.B)
      waitForCheck(dut)
      dut.clock.step()
      dut.io.debug.fill.expect(true.B)
    }
  }

  it should "move to the evict state after a dirty write hit" in {
    test(mkCacheMem()) { dut =>
      waitForIdle(dut)
      fillCacheLine(dut, 0, Seq(1, 2))
      writeCache(dut, 2, 3)
      dut.io.in.wr.poke(true.B)
      dut.io.in.addr.poke(0x20.U)
      waitForCheck(dut)
      dut.io.in.wr.poke(false.B)
      dut.clock.step()
      dut.io.debug.evict.expect(true.B)
    }
  }

  it should "move to the fill state after an eviction" in {
    test(mkCacheMem()) { dut =>
      waitForIdle(dut)
      fillCacheLine(dut, 0, Seq(1, 2))
      writeCache(dut, 2, 3)
      dut.io.in.wr.poke(true.B)
      dut.io.in.addr.poke(0x20.U)
      waitForEvict(dut)
      dut.clock.step(2)
      dut.io.debug.fill.expect(true.B)
    }
  }

  it should "move to the merge state after a line fill" in {
    test(mkCacheMem()) { dut =>
      waitForIdle(dut)
      fillCacheLine(dut, 0, Seq(1, 2))
      writeCache(dut, 2, 3)
      dut.io.in.wr.poke(true.B)
      dut.io.in.addr.poke(0x20.U)
      waitForFill(dut)
      dut.io.out.valid.poke(true.B)
      dut.clock.step(3)
      dut.io.debug.merge.expect(true.B)
    }
  }

  it should "return to the idle state after writing a cache entry" in {
    test(mkCacheMem()) { dut =>
      dut.io.in.rd.poke(true.B)
      waitForFill(dut)
      dut.io.in.rd.poke(false.B)
      dut.io.out.valid.poke(true.B)
      waitForWrite(dut)
      dut.clock.step()
      dut.io.debug.idle.expect(true.B)
    }
  }

  it should "return to the latch state after writing a cache entry (chained)" in {
    test(mkCacheMem()) { dut =>
      dut.io.in.rd.poke(true.B)
      waitForFill(dut)
      dut.io.out.valid.poke(true.B)
      waitForWrite(dut)
      dut.clock.step()
      dut.io.debug.latch.expect(true.B)
    }
  }

  it should "return to the idle state after a read hit" in {
    test(mkCacheMem()) { dut =>
      waitForIdle(dut)
      fillCacheLine(dut, 0, Seq(1, 2))
      dut.io.in.rd.poke(true.B)
      waitForCheck(dut)
      dut.io.in.rd.poke(false.B)
      dut.clock.step()
      dut.io.debug.idle.expect(true.B)
    }
  }

  it should "return to the latch state after a read hit (chained)" in {
    test(mkCacheMem()) { dut =>
      waitForIdle(dut)
      fillCacheLine(dut, 0, Seq(1, 2))
      dut.io.in.rd.poke(true.B)
      waitForCheck(dut)
      dut.clock.step()
      dut.io.debug.latch.expect(true.B)
    }
  }

  it should "return to the idle state after a write hit" in {
    test(mkCacheMem()) { dut =>
      waitForIdle(dut)
      fillCacheLine(dut, 0, Seq(1, 2))
      dut.io.in.wr.poke(true.B)
      waitForCheck(dut)
      dut.io.in.wr.poke(false.B)
      waitForWrite(dut)
      dut.clock.step()
      dut.io.debug.idle.expect(true.B)
    }
  }

  it should "return to the latch state after a write hit (chained)" in {
    test(mkCacheMem()) { dut =>
      waitForIdle(dut)
      fillCacheLine(dut, 0, Seq(1, 2))
      dut.io.in.wr.poke(true.B)
      waitForWrite(dut)
      dut.clock.step()
      dut.io.debug.latch.expect(true.B)
    }
  }

  behavior of "idle"

  it should "deassert the wait signal" in {
    test(mkCacheMem()) { dut =>
      waitForIdle(dut)
      dut.io.in.waitReq.expect(false.B)
    }
  }

  behavior of "read"

  it should "read from the cache during a hit" in {
    test(mkCacheMem()) { dut =>
      waitForIdle(dut)
      fillCacheLine(dut, 0, Seq(0x1234, 0x5678))

      // Read
      dut.io.in.rd.poke(true.B)
      dut.io.in.addr.poke(0.U)
      dut.clock.step()
      dut.io.out.rd.expect(false.B)
      dut.io.out.wr.expect(false.B)
      dut.clock.step()
      dut.io.in.valid.expect(true.B)
      dut.io.in.dout.expect(0x34.U)

      // Read
      dut.io.in.addr.poke(3.U)
      dut.clock.step()
      dut.io.out.rd.expect(false.B)
      dut.io.out.wr.expect(false.B)
      dut.clock.step()
      dut.io.in.valid.expect(true.B)
      dut.io.in.dout.expect(0x56.U)
    }
  }

  it should "fill a cache line during a miss" in {
    test(mkCacheMem()) { dut =>
      waitForIdle(dut)

      // Read
      dut.io.in.rd.poke(true.B)
      dut.io.in.addr.poke(3.U)
      dut.clock.step()
      dut.io.in.rd.poke(false.B)
      dut.clock.step(2)

      // Line fill
      dut.io.out.rd.expect(true.B)
      dut.io.out.wr.expect(false.B)
      dut.io.out.burstLength.expect(2.U)
      dut.io.out.addr.expect(0x1000.U)
      dut.clock.step()
      dut.io.out.valid.poke(true.B)
      dut.io.out.dout.poke(0x1234.U)
      dut.io.in.valid.expect(false.B)
      dut.clock.step()
      dut.io.out.dout.poke(0x5678.U)
      dut.io.in.valid.expect(false.B)
      dut.io.out.rd.expect(false.B)
      dut.io.out.wr.expect(false.B)
      dut.clock.step()
      dut.io.out.valid.poke(false.B)
      dut.io.in.valid.expect(true.B)
      dut.io.in.dout.expect(0x56.U)
    }
  }

  it should "fill a cache line during a miss (wrapping)" in {
    test(mkCacheMem(cacheConfig.copy(wrapping = true))) { dut =>
      waitForIdle(dut)

      // Read
      dut.io.in.rd.poke(true.B)
      dut.io.in.addr.poke(3.U)
      dut.clock.step()
      dut.io.in.rd.poke(false.B)
      dut.clock.step(2)

      // Line fill
      dut.io.out.rd.expect(true.B)
      dut.io.out.wr.expect(false.B)
      dut.io.out.burstLength.expect(2.U)
      dut.io.out.addr.expect(0x1002.U)
      dut.clock.step()
      dut.io.out.valid.poke(true.B)
      dut.io.out.dout.poke(0x1234.U)
      dut.io.in.valid.expect(false.B)
      dut.clock.step()
      dut.io.out.dout.poke(0x5678.U)
      dut.io.in.valid.expect(true.B)
      dut.io.in.dout.expect(0x12.U)
      dut.io.out.rd.expect(false.B)
      dut.io.out.wr.expect(false.B)
      dut.clock.step()
      dut.io.out.valid.poke(false.B)
      dut.io.in.valid.expect(false.B)
    }
  }

  it should "evict a dirty cache entry before a line fill" in {
    test(mkCacheMem(cacheConfig.copy(wrapping = true))) { dut =>
      waitForIdle(dut)
      fillCacheLine(dut, 0, Seq(0x1234, 0x5678))
      writeCache(dut, 2, 0xab)

      // Read
      dut.io.in.rd.poke(true.B)
      dut.io.in.addr.poke(0x23.U)
      dut.clock.step()
      dut.io.in.rd.poke(false.B)
      dut.clock.step(2)

      // Evict
      dut.io.out.rd.expect(false.B)
      dut.io.out.wr.expect(true.B)
      dut.io.out.burstLength.expect(2.U)
      dut.io.out.addr.expect(0x1000.U)
      dut.io.out.din.expect(0x1234.U)
      dut.clock.step()
      dut.io.out.rd.expect(false.B)
      dut.io.out.wr.expect(true.B)
      dut.io.out.din.expect(0x56ab.U)
      dut.clock.step()

      // Line fill
      dut.io.out.rd.expect(true.B)
      dut.io.out.wr.expect(false.B)
      dut.io.out.burstLength.expect(2.U)
      dut.io.out.addr.expect(0x1022.U)
      dut.clock.step()
      dut.io.out.valid.poke(true.B)
      dut.io.out.dout.poke(0x1234.U)
      dut.io.in.valid.expect(false.B)
      dut.clock.step()
      dut.io.out.dout.poke(0x5678.U)
      dut.io.in.valid.expect(true.B)
      dut.io.in.dout.expect(0x12.U)
      dut.io.out.rd.expect(false.B)
      dut.io.out.wr.expect(false.B)
      dut.clock.step()
      dut.io.out.valid.poke(false.B)
      dut.io.in.valid.expect(false.B)
    }
  }

  it should "assert the wait signal" in {
    test(mkCacheMem()) { dut =>
      dut.io.in.rd.poke(true.B)
      dut.io.in.waitReq.expect(true.B)
      waitForIdle(dut)
      dut.io.in.waitReq.expect(false.B)
      waitForCheck(dut)
      dut.io.in.waitReq.expect(true.B)
    }
  }

  behavior of "write"

  it should "write to the cache during a hit" in {
    test(mkCacheMem()) { dut =>
      waitForIdle(dut)
      fillCacheLine(dut, 0, Seq(0x1234, 0x5678))

      // Write
      dut.io.in.wr.poke(true.B)
      dut.io.in.addr.poke(0.U)
      dut.io.in.din.poke(0xab.U)
      dut.clock.step()
      dut.io.out.rd.expect(false.B)
      dut.io.out.wr.expect(false.B)
      dut.clock.step(3)

      // Write
      dut.io.in.addr.poke(3.U)
      dut.io.in.din.poke(0xcd.U)
      dut.clock.step()
      dut.io.out.rd.expect(false.B)
      dut.io.out.wr.expect(false.B)
      dut.clock.step(3)

      readCache(dut, 0) shouldBe 0xab
      readCache(dut, 1) shouldBe 0x12
      readCache(dut, 2) shouldBe 0x78
      readCache(dut, 3) shouldBe 0xcd
    }
  }

  it should "fill a cache line during a miss" in {
    test(mkCacheMem()) { dut =>
      waitForIdle(dut)

      // Write
      dut.io.in.wr.poke(true.B)
      dut.io.in.addr.poke(1.U)
      dut.io.in.din.poke(0xab.U)
      dut.clock.step()
      dut.io.in.wr.poke(false.B)
      dut.clock.step(2)

      // Line fill & merge
      dut.io.out.rd.expect(true.B)
      dut.io.out.wr.expect(false.B)
      dut.io.out.addr.expect(0x1000.U)
      dut.io.out.burstLength.expect(2.U)
      dut.clock.step()
      dut.io.out.valid.poke(true.B)
      dut.io.out.dout.poke(0x1234.U)
      dut.io.in.valid.expect(false.B)
      dut.clock.step()
      dut.io.out.dout.poke(0x5678.U)
      dut.io.in.valid.expect(false.B)
      dut.io.out.rd.expect(false.B)
      dut.io.out.wr.expect(false.B)
      dut.clock.step()
      dut.io.out.valid.poke(false.B)
      dut.clock.step()

      readCache(dut, 0) shouldBe 0x34
      readCache(dut, 1) shouldBe 0xab
      readCache(dut, 2) shouldBe 0x78
      readCache(dut, 3) shouldBe 0x56
    }
  }

  it should "evict a dirty cache entry before writing to the cache" in {
    test(mkCacheMem()) { dut =>
      waitForIdle(dut)
      fillCacheLine(dut, 0, Seq(0x1234, 0x5678))
      writeCache(dut, 2, 0xab)

      // Write
      dut.io.in.wr.poke(true.B)
      dut.io.in.addr.poke(0x20.U)
      dut.io.in.din.poke(0xcd.U)
      dut.clock.step()
      dut.io.in.wr.poke(false.B)
      dut.clock.step(2)

      // Evict
      dut.io.out.rd.expect(false.B)
      dut.io.out.wr.expect(true.B)
      dut.io.out.burstLength.expect(2.U)
      dut.io.out.addr.expect(0x1000.U)
      dut.io.out.din.expect(0x1234.U)
      dut.clock.step()
      dut.io.out.rd.expect(false.B)
      dut.io.out.wr.expect(true.B)
      dut.io.out.din.expect(0x56ab.U)
      dut.clock.step()

      // Line fill & merge
      dut.io.out.rd.expect(true.B)
      dut.io.out.wr.expect(false.B)
      dut.io.out.burstLength.expect(2.U)
      dut.io.out.addr.expect(0x1020.U)
      dut.clock.step()
      dut.io.out.valid.poke(true.B)
      dut.io.out.dout.poke(0x1234.U)
      dut.io.in.valid.expect(false.B)
      dut.clock.step()
      dut.io.out.dout.poke(0x5678.U)
      dut.io.in.valid.expect(false.B)
      dut.io.out.rd.expect(false.B)
      dut.io.out.wr.expect(false.B)
      dut.clock.step()
      dut.io.out.valid.poke(false.B)
      dut.io.in.valid.expect(false.B)
      dut.clock.step()

      readCache(dut, 0x20) shouldBe 0xcd
      readCache(dut, 0x21) shouldBe 0x12
      readCache(dut, 0x22) shouldBe 0x78
      readCache(dut, 0x23) shouldBe 0x56
    }
  }

  it should "assert the wait signal" in {
    test(mkCacheMem()) { dut =>
      dut.io.in.wr.poke(true.B)
      dut.io.in.waitReq.expect(true.B)
      waitForIdle(dut)
      dut.io.in.waitReq.expect(false.B)
      waitForCheck(dut)
      dut.io.in.waitReq.expect(true.B)
    }
  }

  behavior of "data width ratios"

  it should "read a word (8:8)" in {
    test(mkCacheMem(cacheConfig.copy(inDataWidth = 8, outDataWidth = 8))) { dut =>
      waitForIdle(dut)
      fillCacheLine(dut, 0, Seq(0x12, 0x34))
      readCache(dut, 0) shouldBe 0x12
      readCache(dut, 1) shouldBe 0x34
    }
  }

  it should "read a word (8:16)" in {
    test(mkCacheMem(cacheConfig.copy(inDataWidth = 8, outDataWidth = 16))) { dut =>
      waitForIdle(dut)
      fillCacheLine(dut, 0, Seq(0x1234, 0x5678))
      readCache(dut, 0) shouldBe 0x34
      readCache(dut, 1) shouldBe 0x12
      readCache(dut, 2) shouldBe 0x78
      readCache(dut, 3) shouldBe 0x56
    }
  }

  it should "read a word (16:8)" in {
    test(mkCacheMem(cacheConfig.copy(inDataWidth = 16, outDataWidth = 8))) { dut =>
      waitForIdle(dut)
      fillCacheLine(dut, 0, Seq(0x12, 0x34))
      readCache(dut, 0) shouldBe 0x3412
    }
  }

  it should "read a word (16:16)" in {
    test(mkCacheMem(cacheConfig.copy(inDataWidth = 16, outDataWidth = 16))) { dut =>
      waitForIdle(dut)
      fillCacheLine(dut, 0, Seq(0x1234, 0x5678))
      readCache(dut, 0) shouldBe 0x1234
      readCache(dut, 2) shouldBe 0x5678
    }
  }
}
