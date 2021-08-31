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

package cave.gpu

import chisel3._
import chiseltest._
import org.scalatest._

trait SpriteProcessorTestHelpers {
  def mkProcessor(maxSprites: Int = 2) = new SpriteProcessor(maxSprites)

  def waitForIdle(dut: SpriteProcessor) =
    while (!dut.io.debug.idle.peek().litToBoolean) { dut.clock.step() }

  def waitForLoad(dut: SpriteProcessor) =
    while (!dut.io.debug.load.peek().litToBoolean) { dut.clock.step() }

  def waitForLatch(dut: SpriteProcessor) =
    while (!dut.io.debug.latch.peek().litToBoolean) { dut.clock.step() }

  def waitForCheck(dut: SpriteProcessor) =
    while (!dut.io.debug.check.peek().litToBoolean) { dut.clock.step() }

  def waitForReady(dut: SpriteProcessor) =
    while (!dut.io.debug.ready.peek().litToBoolean) { dut.clock.step() }

  def waitForPending(dut: SpriteProcessor) =
    while (!dut.io.debug.pending.peek().litToBoolean) { dut.clock.step() }

  def waitForNext(dut: SpriteProcessor) =
    while (!dut.io.debug.next.peek().litToBoolean) { dut.clock.step() }

  def waitForDone(dut: SpriteProcessor) =
    while (!dut.io.debug.done.peek().litToBoolean) { dut.clock.step() }
}

class SpriteProcessorTest extends FlatSpec with ChiselScalatestTester with Matchers with SpriteProcessorTestHelpers {
  behavior of "FSM"

  it should "move to the load state when the start signal is asserted" in {
    test(mkProcessor()) { dut =>
      dut.io.start.poke(true.B)
      dut.clock.step()
      dut.io.debug.load.expect(true.B)
    }
  }

  it should "move to the latch state after loading a sprite" in {
    test(mkProcessor()) { dut =>
      dut.io.start.poke(true.B)
      waitForLoad(dut)
      dut.clock.step()
      dut.io.debug.latch.expect(true.B)
    }
  }

  it should "move to the check state after latching a sprite" in {
    test(mkProcessor()) { dut =>
      dut.io.start.poke(true.B)
      waitForLatch(dut)
      dut.clock.step()
      dut.io.debug.check.expect(true.B)
    }
  }

  it should "move to the next state after checking an invisible sprite" in {
    test(mkProcessor()) { dut =>
      dut.io.start.poke(true.B)
      waitForCheck(dut)
      dut.clock.step()
      dut.io.debug.next.expect(true.B)
    }
  }

  it should "move to the ready state after checking a visible sprite" in {
    test(mkProcessor()) { dut =>
      dut.io.start.poke(true.B)
      dut.io.spriteRam.dout.poke("h0101_0000_0000_0000_0000".U)
      waitForCheck(dut)
      dut.clock.step()
      dut.io.debug.ready.expect(true.B)
    }
  }

  it should "move to the done state after blitting all sprites" in {
    test(mkProcessor(1)) { dut =>
      dut.io.start.poke(true.B)
      waitForNext(dut)
      dut.clock.step()
      dut.io.debug.done.expect(true.B)
    }
  }

  it should "move to the idle state after the blit is done" in {
    test(mkProcessor()) { dut =>
      dut.io.start.poke(true.B)
      waitForDone(dut)
      dut.clock.step()
      dut.io.debug.idle.expect(true.B)
    }
  }

  behavior of "busy flag"

  it should "assert the busy flag when the processor has started" in {
    test(mkProcessor()) { dut =>
      dut.io.busy.expect(false.B)
      dut.io.start.poke(true.B)
      dut.clock.step()
      dut.io.busy.expect(true.B)
    }
  }

  it should "deassert the busy flag when the processor has finished" in {
    test(mkProcessor(1)) { dut =>
      dut.io.start.poke(true.B)
      dut.io.spriteRam.dout.poke("h0101_0000_0000_0001_0000".U)
      waitForPending(dut)
      dut.io.tileRom.valid.poke(true.B)
      dut.clock.step(16)
      dut.io.tileRom.valid.poke(false.B)
      dut.io.tileRom.burstDone.poke(true.B)
      waitForDone(dut)
      dut.io.busy.expect(true.B)
      dut.clock.step(245)
      dut.io.busy.expect(false.B)
    }
  }

  behavior of "sprite data"

  it should "fetch sprite data from the sprite RAM" in {
    test(mkProcessor()) { dut =>
      dut.io.start.poke(true.B)
      waitForLoad(dut)
      dut.io.spriteRam.rd.expect(true.B)
    }
  }

  behavior of "pixel data"

  it should "fetch pixel data from the tile ROM" in {
    test(mkProcessor()) { dut =>
      dut.io.start.poke(true.B)
      dut.io.spriteRam.dout.poke("h0101_0000_0000_0001_0000".U)
      waitForReady(dut)
      dut.clock.step()
      dut.io.tileRom.rd.expect(true.B)
      dut.io.tileRom.addr.expect(0x80.U)
      dut.io.tileRom.burstLength.expect(16.U)
      dut.clock.step()
      dut.io.tileRom.rd.expect(false.B)
    }
  }
}
