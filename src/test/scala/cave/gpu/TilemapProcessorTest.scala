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

package cave.gpu

import cave.Config
import chisel3._
import chiseltest._
import org.scalatest._
import flatspec.AnyFlatSpec
import matchers.should.Matchers

trait TilemapProcessorTestHelpers {
  def mkProcessor() = new TilemapProcessor(1, 1)

  def waitForIdle(dut: TilemapProcessor) =
    while (!dut.io.debug.idle.peekBoolean()) { dut.clock.step() }

  def waitForCheck(dut: TilemapProcessor) =
    while (!dut.io.debug.check.peekBoolean()) { dut.clock.step() }

  def waitForLoad(dut: TilemapProcessor) =
    while (!dut.io.debug.load.peekBoolean()) { dut.clock.step() }

  def waitForLatch(dut: TilemapProcessor) =
    while (!dut.io.debug.latch.peekBoolean()) { dut.clock.step() }

  def waitForReady(dut: TilemapProcessor) =
    while (!dut.io.debug.ready.peekBoolean()) { dut.clock.step() }

  def waitForPending(dut: TilemapProcessor) =
    while (!dut.io.debug.pending.peekBoolean()) { dut.clock.step() }

  def waitForNext(dut: TilemapProcessor) =
    while (!dut.io.debug.next.peekBoolean()) { dut.clock.step() }

  def waitForDone(dut: TilemapProcessor) =
    while (!dut.io.debug.done.peekBoolean()) { dut.clock.step() }
}

class TilemapProcessorTest extends AnyFlatSpec with ChiselScalatestTester with Matchers with TilemapProcessorTestHelpers {
  behavior of "FSM"

  it should "move to the check state when the start signal is asserted" in {
    test(mkProcessor()) { dut =>
      dut.io.start.poke(true)
      dut.clock.step()
      dut.io.debug.check.expect(true)
    }
  }

  it should "move to the idle state after checking a disabled layer" in {
    test(mkProcessor()) { dut =>
      dut.io.layer.disable.poke(true)
      dut.io.start.poke(true)
      waitForCheck(dut)
      dut.clock.step()
      dut.io.debug.idle.expect(true)
    }
  }

  it should "move to the load state after checking an enabled layer" in {
    test(mkProcessor()) { dut =>
      dut.io.gameConfig.layer0Format.poke(Config.GFX_FORMAT_8BPP)
      dut.io.start.poke(true)
      waitForCheck(dut)
      dut.clock.step()
      dut.io.debug.load.expect(true)
    }
  }

  it should "move to the latch state after loading a tile" in {
    test(mkProcessor()) { dut =>
      dut.io.gameConfig.layer0Format.poke(Config.GFX_FORMAT_8BPP)
      dut.io.start.poke(true)
      waitForLoad(dut)
      dut.clock.step()
      dut.io.debug.latch.expect(true)
    }
  }

  it should "move to the ready state after latching a tile" in {
    test(mkProcessor()) { dut =>
      dut.io.gameConfig.layer0Format.poke(Config.GFX_FORMAT_8BPP)
      dut.io.start.poke(true)
      waitForLatch(dut)
      dut.clock.step()
      dut.io.debug.ready.expect(true)
    }
  }

  it should "move to the pending state state after starting the blitter" in {
    test(mkProcessor()) { dut =>
      dut.io.gameConfig.layer0Format.poke(Config.GFX_FORMAT_8BPP)
      dut.io.start.poke(true)
      waitForReady(dut)
      dut.clock.step()
      dut.io.debug.pending.expect(true)
    }
  }

  it should "move to the next state state after reading pixel data" in {
    test(mkProcessor()) { dut =>
      dut.io.gameConfig.layer0Format.poke(Config.GFX_FORMAT_8BPP)
      dut.io.start.poke(true)
      waitForPending(dut)
      dut.clock.step()
      dut.io.debug.next.expect(true)
    }
  }

  behavior of "busy flag"

  it should "assert the busy flag when the processor has started" in {
    test(mkProcessor()) { dut =>
      dut.io.busy.expect(false)
      dut.io.start.poke(true)
      dut.clock.step()
      dut.io.busy.expect(true)
    }
  }

  behavior of "layer data"

  it should "fetch tile data from the layer RAM" in {
    test(mkProcessor()) { dut =>
      dut.io.gameConfig.layer0Format.poke(Config.GFX_FORMAT_8BPP)
      dut.io.start.poke(true)
      waitForLoad(dut)
      dut.io.layerRam.rd.expect(true)
    }
  }

  behavior of "pixel data"

  it should "fetch pixel data from the tile ROM" in {
    test(mkProcessor()) { dut =>
      dut.io.gameConfig.layer0Format.poke(Config.GFX_FORMAT_8BPP)
      dut.io.start.poke(true)
      dut.io.layerRam.dout.poke("h0001_0000".U)
      waitForLatch(dut)
      dut.clock.step()
      dut.io.tileRom.rd.expect(true)
      dut.io.tileRom.addr.expect(0x40.U)
      dut.io.tileRom.burstLength.expect(8.U)
      dut.clock.step()
      dut.io.tileRom.rd.expect(false)
    }
  }
}
