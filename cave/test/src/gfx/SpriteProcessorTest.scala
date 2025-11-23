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
import chisel3.simulator.scalatest.ChiselSim
import org.scalatest._
import flatspec.AnyFlatSpec
import matchers.should.Matchers

class SpriteProcessorTest extends AnyFlatSpec with ChiselSim with Matchers {
  private def mkProcessor(maxSprites: Int = 2) = new SpriteProcessor(maxSprites = maxSprites)

  behavior of "FSM"

  it should "move to the load state when the start signal is asserted" in {
    simulate(mkProcessor()) { dut =>
      dut.io.ctrl.start.poke(true)
      dut.clock.step()
      dut.io.debug.load.expect(true)
    }
  }

  it should "move to the latch state after loading a sprite" in {
    simulate(mkProcessor()) { dut =>
      dut.io.ctrl.start.poke(true)
      dut.clock.stepUntil(dut.io.debug.load, 1, 10)
      dut.clock.step()
      dut.io.debug.latch.expect(true)
    }
  }

  it should "move to the check state after latching a sprite" in {
    simulate(mkProcessor()) { dut =>
      dut.io.ctrl.start.poke(true)
      dut.clock.stepUntil(dut.io.debug.latch, 1, 10)
      dut.clock.step()
      dut.io.debug.check.expect(true)
    }
  }

  it should "move to the next state after checking an invisible sprite" in {
    simulate(mkProcessor()) { dut =>
      dut.io.ctrl.start.poke(true)
      dut.clock.stepUntil(dut.io.debug.check, 1, 10)
      dut.clock.step()
      dut.io.debug.next.expect(true)
    }
  }

  it should "move to the ready state after checking a visible sprite" in {
    simulate(mkProcessor()) { dut =>
      dut.io.ctrl.start.poke(true)
      dut.io.ctrl.vram.dout.poke("h0101_0000_0000_0000_0000".U)
      dut.clock.stepUntil(dut.io.debug.check, 1, 10)
      dut.clock.step()
      dut.io.debug.ready.expect(true)
    }
  }

  it should "move to the done state after blitting all sprites" in {
    simulate(mkProcessor(1)) { dut =>
      dut.io.ctrl.start.poke(true)
      dut.clock.stepUntil(dut.io.debug.next, 1, 10)
      dut.clock.step()
      dut.io.debug.done.expect(true)
    }
  }

  it should "move to the idle state after the blit is done" in {
    simulate(mkProcessor()) { dut =>
      dut.io.ctrl.start.poke(true)
      dut.clock.stepUntil(dut.io.debug.done, 1, 10)
      dut.clock.step()
      dut.io.debug.idle.expect(true)
    }
  }

  behavior of "busy flag"

  it should "assert the busy flag when the processor has started" in {
    simulate(mkProcessor()) { dut =>
      dut.io.ctrl.busy.expect(false)
      dut.io.ctrl.start.poke(true)
      dut.clock.step()
      dut.io.ctrl.busy.expect(true)
    }
  }

  it should "deassert the busy flag when the processor has finished" in {
    simulate(mkProcessor(1)) { dut =>
      dut.io.ctrl.start.poke(true)
      dut.io.ctrl.vram.dout.poke("h0101_0000_0000_0001_0000".U)
      dut.clock.stepUntil(dut.io.debug.pending, 1, 10)
      dut.io.ctrl.tileRom.wait_n.poke(true)
      dut.io.ctrl.tileRom.valid.poke(true)
      dut.io.frameBuffer.wait_n.poke(true)
      dut.clock.step(16)
      dut.io.ctrl.tileRom.valid.poke(false)
      dut.io.ctrl.tileRom.burstDone.poke(true)
      dut.clock.stepUntil(dut.io.debug.done, 1, 10)
      dut.io.ctrl.busy.expect(true)
      dut.clock.step(244)
      dut.io.ctrl.busy.expect(false)
    }
  }

  behavior of "sprite data"

  it should "fetch sprite data from the sprite RAM" in {
    simulate(mkProcessor()) { dut =>
      dut.io.ctrl.start.poke(true)
      dut.clock.stepUntil(dut.io.debug.load, 1, 10)
      dut.io.ctrl.vram.rd.expect(true)
    }
  }

  behavior of "pixel data"

  it should "fetch pixel data from the tile ROM" in {
    simulate(mkProcessor()) { dut =>
      dut.io.ctrl.start.poke(true)
      dut.io.ctrl.vram.dout.poke("h0101_0000_0000_0001_0000".U)
      dut.io.ctrl.tileRom.wait_n.poke(true)
      dut.io.frameBuffer.wait_n.poke(true)
      dut.clock.stepUntil(dut.io.debug.ready, 1, 10)
      dut.clock.step()
      dut.io.ctrl.tileRom.rd.expect(true)
      dut.io.ctrl.tileRom.addr.expect(0x80.U)
      dut.io.ctrl.tileRom.burstLength.expect(16.U)
      dut.clock.step()
      dut.io.ctrl.tileRom.rd.expect(false)
    }
  }
}
