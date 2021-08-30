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

import cave.Config
import chisel3._
import chiseltest._
import org.scalatest._

trait TilemapProcessorTestHelpers {
  def mkProcessor() = new TilemapProcessor

  def waitForIdle(dut: TilemapProcessor) =
    while (!dut.io.debug.idle.peek().litToBoolean) { dut.clock.step() }

  def waitForCheck(dut: TilemapProcessor) =
    while (!dut.io.debug.check.peek().litToBoolean) { dut.clock.step() }

  def waitForLoad(dut: TilemapProcessor) =
    while (!dut.io.debug.load.peek().litToBoolean) { dut.clock.step() }

  def waitForLatch(dut: TilemapProcessor) =
    while (!dut.io.debug.latch.peek().litToBoolean) { dut.clock.step() }

  def waitForReady(dut: TilemapProcessor) =
    while (!dut.io.debug.ready.peek().litToBoolean) { dut.clock.step() }

  def waitForPending(dut: TilemapProcessor) =
    while (!dut.io.debug.pending.peek().litToBoolean) { dut.clock.step() }

  def waitForNext(dut: TilemapProcessor) =
    while (!dut.io.debug.next.peek().litToBoolean) { dut.clock.step() }

  def waitForDone(dut: TilemapProcessor) =
    while (!dut.io.debug.done.peek().litToBoolean) { dut.clock.step() }
}

class TilemapProcessorTest extends FlatSpec with ChiselScalatestTester with Matchers with TilemapProcessorTestHelpers {
  behavior of "FSM"

  it should "move to the check state when the start signal is asserted" in {
    test(mkProcessor()) { dut =>
      dut.io.start.poke(true.B)
      dut.clock.step()
      dut.io.debug.check.expect(true.B)
    }
  }

  it should "move to the idle state after checking a disabled layer" in {
    test(mkProcessor()) { dut =>
      dut.io.layer.disable.poke(true.B)
      dut.io.start.poke(true.B)
      waitForCheck(dut)
      dut.clock.step()
      dut.io.debug.idle.expect(true.B)
    }
  }

  it should "move to the load state after checking an enabled layer" in {
    test(mkProcessor()) { dut =>
      dut.io.gameConfig.layer0Format.poke(Config.GFX_FORMAT_8BPP.U)
      dut.io.start.poke(true.B)
      waitForCheck(dut)
      dut.clock.step()
      dut.io.debug.load.expect(true.B)
    }
  }

  it should "move to the latch state after loading a tile" in {
    test(mkProcessor()) { dut =>
      dut.io.gameConfig.layer0Format.poke(Config.GFX_FORMAT_8BPP.U)
      dut.io.start.poke(true.B)
      waitForLoad(dut)
      dut.clock.step()
      dut.io.debug.latch.expect(true.B)
    }
  }

  it should "move to the ready state after latching a tile" in {
    test(mkProcessor()) { dut =>
      dut.io.gameConfig.layer0Format.poke(Config.GFX_FORMAT_8BPP.U)
      dut.io.start.poke(true.B)
      waitForLatch(dut)
      dut.clock.step()
      dut.io.debug.ready.expect(true.B)
    }
  }

  it should "move to the pending state state after starting the blitter" in {
    test(mkProcessor()) { dut =>
      dut.io.gameConfig.layer0Format.poke(Config.GFX_FORMAT_8BPP.U)
      dut.io.start.poke(true.B)
      waitForReady(dut)
      dut.clock.step()
      dut.io.debug.pending.expect(true.B)
    }
  }

  it should "move to the next state state after reading pixel data" in {
    test(mkProcessor()) { dut =>
      dut.io.gameConfig.layer0Format.poke(Config.GFX_FORMAT_8BPP.U)
      dut.io.start.poke(true.B)
      waitForPending(dut)
      dut.clock.step()
      dut.io.debug.next.expect(true.B)
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

  behavior of "layer data"

  it should "fetch tile data from the layer RAM" in {
    test(mkProcessor()) { dut =>
      dut.io.gameConfig.layer0Format.poke(Config.GFX_FORMAT_8BPP.U)
      dut.io.start.poke(true.B)
      dut.clock.step(2)
      dut.io.layerRam.rd.expect(true.B)
    }
  }

  behavior of "pixel data"

  it should "fetch pixel data from the tile ROM" in {
    test(mkProcessor()) { dut =>
      dut.io.gameConfig.layer0Format.poke(Config.GFX_FORMAT_8BPP.U)
      dut.io.start.poke(true.B)
      dut.io.layerRam.dout.poke("h0001_0000".U)
      waitForLatch(dut)
      dut.clock.step()
      dut.io.tileRom.rd.expect(true.B)
      dut.io.tileRom.addr.expect(0x40.U)
      dut.io.tileRom.burstLength.expect(8.U)
      dut.clock.step()
      dut.io.tileRom.rd.expect(false.B)
    }
  }
}
