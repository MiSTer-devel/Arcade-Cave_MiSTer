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

import cave.{Config, GraphicsFormat}
import chisel3._
import chisel3.simulator.scalatest.ChiselSim
import org.scalatest._
import flatspec.AnyFlatSpec
import matchers.should.Matchers

class SpriteDecoderTest extends AnyFlatSpec with ChiselSim with Matchers {
  behavior of "4BPP"

  it should "request a word from the tile ROM for every row" in {
    simulate(new SpriteDecoder) { dut =>
      // start
      dut.io.format.poke(GraphicsFormat.GFX_FORMAT_4BPP)
      dut.io.pixelData.ready.poke(true)
      dut.clock.step()

      // request 0
      dut.io.tileRom.ready.expect(true)
      dut.clock.step()
      dut.io.tileRom.valid.poke(true)
      dut.clock.step()
      dut.io.tileRom.valid.poke(false)
      dut.io.pixelData.valid.expect(true)
      dut.io.tileRom.ready.expect(false)
      dut.clock.step()

      // request 1
      dut.io.tileRom.ready.expect(true)
      dut.clock.step()
      dut.io.tileRom.valid.poke(true)
      dut.clock.step()
      dut.io.tileRom.valid.poke(false)
      dut.io.pixelData.valid.expect(true)
      dut.io.tileRom.ready.expect(false)
      dut.clock.step()
    }
  }

  it should "decode a 4BPP tile" in {
    simulate(new SpriteDecoder) { dut =>
      dut.io.format.poke(GraphicsFormat.GFX_FORMAT_4BPP)
      dut.io.pixelData.ready.poke(true)
      dut.clock.step()
      dut.io.tileRom.valid.poke(true)
      dut.io.tileRom.bits.poke("h_fedcba98_76543210".U)
      dut.clock.step()
      dut.io.pixelData.valid.expect(true)
      dut.io.pixelData.bits(0).expect(0x0)
      dut.io.pixelData.bits(1).expect(0x1)
      dut.io.pixelData.bits(14).expect(0xe)
      dut.io.pixelData.bits(15).expect(0xf)
    }
  }

  it should "decode a 4BPP MSB tile" in {
    simulate(new SpriteDecoder) { dut =>
      dut.io.format.poke(GraphicsFormat.GFX_FORMAT_4BPP_MSB)
      dut.io.pixelData.ready.poke(true)
      dut.clock.step()
      dut.io.tileRom.valid.poke(true)
      dut.io.tileRom.bits.poke("h_fedcba98_76543210".U)
      dut.clock.step()
      dut.io.pixelData.valid.expect(true)
      dut.io.pixelData.bits(0).expect(0x3)
      dut.io.pixelData.bits(1).expect(0x2)
      dut.io.pixelData.bits(14).expect(0xd)
      dut.io.pixelData.bits(15).expect(0xc)
    }
  }

  behavior of "8BPP"

  it should "request two words from the tile ROM for every row" in {
    simulate(new SpriteDecoder) { dut =>
      // start
      dut.io.format.poke(GraphicsFormat.GFX_FORMAT_8BPP)
      dut.io.pixelData.ready.poke(true)
      dut.clock.step()

      // request 0
      dut.io.tileRom.ready.expect(true)
      dut.clock.step()
      dut.io.pixelData.valid.expect(false)
      dut.io.tileRom.valid.poke(true)
      dut.clock.step()
      dut.io.pixelData.valid.expect(false)
      dut.clock.step()
      dut.io.pixelData.valid.expect(true)
      dut.io.tileRom.ready.expect(false)
      dut.io.tileRom.valid.poke(false)
      dut.clock.step()

      // request 1
      dut.io.tileRom.ready.expect(true)
      dut.clock.step()
      dut.io.pixelData.valid.expect(false)
      dut.io.tileRom.valid.poke(true)
      dut.clock.step()
      dut.io.pixelData.valid.expect(false)
      dut.clock.step()
      dut.io.pixelData.valid.expect(true)
      dut.io.tileRom.ready.expect(false)
      dut.io.tileRom.valid.poke(false)
      dut.clock.step()
    }
  }

  it should "decode a 8BPP tile" in {
    simulate(new SpriteDecoder) { dut =>
      dut.io.format.poke(GraphicsFormat.GFX_FORMAT_8BPP)
      dut.io.pixelData.ready.poke(true)
      dut.clock.step()
      dut.io.tileRom.valid.poke(true)
      dut.io.tileRom.bits.poke("h_fedcba98_76543210".U)
      dut.clock.step(2)
      dut.io.pixelData.valid.expect(true)
      dut.io.pixelData.bits(0).expect(0x02)
      dut.io.pixelData.bits(1).expect(0x13)
      dut.io.pixelData.bits(6).expect(0xce)
      dut.io.pixelData.bits(7).expect(0xdf)
    }
  }
}
