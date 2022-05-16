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

package cave.gfx

import chisel3._
import chiseltest._
import org.scalatest._
import flatspec.AnyFlatSpec
import matchers.should.Matchers

trait SpriteProcessorTestHelpers {
  def mkProcessor(maxSprites: Int = 2) = new SpriteProcessor(
    maxSprites = maxSprites,
    clearFrameBuffer = false
  )

  def waitForIdle(dut: SpriteProcessor) =
    while (!dut.io.debug.idle.peekBoolean()) { dut.clock.step() }

  def waitForLoad(dut: SpriteProcessor) =
    while (!dut.io.debug.load.peekBoolean()) { dut.clock.step() }

  def waitForLatch(dut: SpriteProcessor) =
    while (!dut.io.debug.latch.peekBoolean()) { dut.clock.step() }

  def waitForCheck(dut: SpriteProcessor) =
    while (!dut.io.debug.check.peekBoolean()) { dut.clock.step() }

  def waitForReady(dut: SpriteProcessor) =
    while (!dut.io.debug.ready.peekBoolean()) { dut.clock.step() }

  def waitForPending(dut: SpriteProcessor) =
    while (!dut.io.debug.pending.peekBoolean()) { dut.clock.step() }

  def waitForNext(dut: SpriteProcessor) =
    while (!dut.io.debug.next.peekBoolean()) { dut.clock.step() }

  def waitForDone(dut: SpriteProcessor) =
    while (!dut.io.debug.done.peekBoolean()) { dut.clock.step() }
}

class SpriteProcessorTest extends AnyFlatSpec with ChiselScalatestTester with Matchers with SpriteProcessorTestHelpers {
  behavior of "FSM"

  it should "move to the load state when the start signal is asserted" in {
    test(mkProcessor()) { dut =>
      dut.io.ctrl.start.poke(true)
      dut.clock.step()
      dut.io.debug.load.expect(true)
    }
  }

  it should "move to the latch state after loading a sprite" in {
    test(mkProcessor()) { dut =>
      dut.io.ctrl.start.poke(true)
      waitForLoad(dut)
      dut.clock.step()
      dut.io.debug.latch.expect(true)
    }
  }

  it should "move to the check state after latching a sprite" in {
    test(mkProcessor()) { dut =>
      dut.io.ctrl.start.poke(true)
      waitForLatch(dut)
      dut.clock.step()
      dut.io.debug.check.expect(true)
    }
  }

  it should "move to the next state after checking an invisible sprite" in {
    test(mkProcessor()) { dut =>
      dut.io.ctrl.start.poke(true)
      waitForCheck(dut)
      dut.clock.step()
      dut.io.debug.next.expect(true)
    }
  }

  it should "move to the ready state after checking a visible sprite" in {
    test(mkProcessor()) { dut =>
      dut.io.ctrl.start.poke(true)
      dut.io.ctrl.vram.dout.poke("h0101_0000_0000_0000_0000".U)
      waitForCheck(dut)
      dut.clock.step()
      dut.io.debug.ready.expect(true)
    }
  }

  it should "move to the done state after blitting all sprites" in {
    test(mkProcessor(1)) { dut =>
      dut.io.ctrl.start.poke(true)
      waitForNext(dut)
      dut.clock.step()
      dut.io.debug.done.expect(true)
    }
  }

  it should "move to the idle state after the blit is done" in {
    test(mkProcessor()) { dut =>
      dut.io.ctrl.start.poke(true)
      waitForDone(dut)
      dut.clock.step()
      dut.io.debug.idle.expect(true)
    }
  }

  behavior of "busy flag"

  it should "assert the busy flag when the processor has started" in {
    test(mkProcessor()) { dut =>
      dut.io.ctrl.busy.expect(false)
      dut.io.ctrl.start.poke(true)
      dut.clock.step()
      dut.io.ctrl.busy.expect(true)
    }
  }

  it should "deassert the busy flag when the processor has finished" in {
    test(mkProcessor(1)) { dut =>
      dut.io.ctrl.start.poke(true)
      dut.io.ctrl.vram.dout.poke("h0101_0000_0000_0001_0000".U)
      waitForPending(dut)
      dut.io.ctrl.tileRom.valid.poke(true)
      dut.clock.step(16)
      dut.io.ctrl.tileRom.valid.poke(false)
      dut.io.ctrl.tileRom.burstDone.poke(true)
      waitForDone(dut)
      dut.io.ctrl.busy.expect(true)
      dut.clock.step(245)
      dut.io.ctrl.busy.expect(false)
    }
  }

  behavior of "sprite data"

  it should "fetch sprite data from the sprite RAM" in {
    test(mkProcessor()) { dut =>
      dut.io.ctrl.start.poke(true)
      waitForLoad(dut)
      dut.io.ctrl.vram.rd.expect(true)
    }
  }

  behavior of "pixel data"

  it should "fetch pixel data from the tile ROM" in {
    test(mkProcessor()) { dut =>
      dut.io.ctrl.start.poke(true)
      dut.io.ctrl.vram.dout.poke("h0101_0000_0000_0001_0000".U)
      waitForReady(dut)
      dut.clock.step()
      dut.io.ctrl.tileRom.rd.expect(true)
      dut.io.ctrl.tileRom.addr.expect(0x80.U)
      dut.io.ctrl.tileRom.burstLength.expect(16.U)
      dut.clock.step()
      dut.io.ctrl.tileRom.rd.expect(false)
    }
  }
}
