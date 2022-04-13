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

class TilemapDecoderTest extends AnyFlatSpec with ChiselScalatestTester with Matchers {
  behavior of "4BPP"

  it should "request a word from the tile ROM for every two rows" in {
    test(new TilemapDecoder) { dut =>
      dut.io.format.poke(Config.GFX_FORMAT_4BPP)
      dut.clock.step()

      // Initial request
      dut.io.rom.valid.poke(true)
      dut.io.rom.ready.expect(true)
      dut.clock.step()
      dut.io.pixelData.valid.expect(true)
      dut.io.rom.ready.expect(false)
      dut.clock.step()

      // First request
      dut.io.pixelData.ready.poke(true)
      dut.clock.step()
      dut.io.pixelData.ready.poke(false)
      dut.io.pixelData.valid.expect(true)
      dut.io.rom.ready.expect(false)
      dut.clock.step()

      // Second request
      dut.io.pixelData.ready.poke(true)
      dut.clock.step()
      dut.io.pixelData.ready.poke(false)
      dut.io.rom.ready.expect(true)
      dut.clock.step()
      dut.io.pixelData.valid.expect(true)
      dut.io.rom.ready.expect(false)

      // Third request
      dut.io.pixelData.ready.poke(true)
      dut.clock.step()
      dut.io.pixelData.ready.poke(false)
      dut.io.pixelData.valid.expect(true)
      dut.io.rom.ready.expect(false)
    }
  }

  it should "decode a 4BPP tile" in {
    test(new TilemapDecoder) { dut =>
      dut.io.format.poke(Config.GFX_FORMAT_4BPP)
      dut.clock.step()
      dut.io.rom.valid.poke(true)
      dut.io.rom.bits.poke("hfedcba9876543210".U)
      dut.clock.step()
      dut.io.pixelData.ready.poke(true)

      // First request
      dut.io.pixelData.valid.expect(true)
      dut.io.pixelData.bits(0).expect(0xf.U)
      dut.io.pixelData.bits(1).expect(0xe.U)
      dut.io.pixelData.bits(6).expect(0x9.U)
      dut.io.pixelData.bits(7).expect(0x8.U)
      dut.clock.step()

      // Second request
      dut.io.pixelData.valid.expect(true)
      dut.io.pixelData.bits(0).expect(0x7.U)
      dut.io.pixelData.bits(1).expect(0x6.U)
      dut.io.pixelData.bits(6).expect(0x1.U)
      dut.io.pixelData.bits(7).expect(0x0.U)
    }
  }

  behavior of "8BPP"

  it should "request a word from the tile ROM for every row" in {
    test(new TilemapDecoder) { dut =>
      dut.io.format.poke(Config.GFX_FORMAT_8BPP)
      dut.clock.step()

      // Initial request
      dut.io.rom.valid.poke(true)
      dut.io.rom.ready.expect(true)
      dut.clock.step()
      dut.io.pixelData.valid.expect(true)
      dut.io.rom.ready.expect(false)
      dut.clock.step()

      // First request
      dut.io.pixelData.ready.poke(true)
      dut.clock.step()
      dut.io.pixelData.ready.poke(false)
      dut.io.rom.ready.expect(true)
      dut.clock.step()
      dut.io.pixelData.valid.expect(true)
      dut.io.rom.ready.expect(false)
      dut.clock.step()

      // Second request
      dut.io.pixelData.ready.poke(true)
      dut.clock.step()
      dut.io.pixelData.ready.poke(false)
      dut.io.rom.ready.expect(true)
      dut.clock.step()
      dut.io.pixelData.valid.expect(true)
      dut.io.rom.ready.expect(false)
    }
  }

  it should "decode a 8BPP tile" in {
    test(new TilemapDecoder) { dut =>
      dut.io.format.poke(Config.GFX_FORMAT_8BPP)
      dut.clock.step()
      dut.io.rom.valid.poke(true)
      dut.io.rom.bits.poke("hfedcba9876543210".U)
      dut.clock.step()
      dut.io.pixelData.valid.expect(true)
      dut.io.pixelData.bits(0).expect(0xdf.U)
      dut.io.pixelData.bits(1).expect(0xce.U)
      dut.io.pixelData.bits(6).expect(0x13.U)
      dut.io.pixelData.bits(7).expect(0x02.U)
    }
  }
}
