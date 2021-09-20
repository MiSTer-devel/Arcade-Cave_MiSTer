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

class TilemapBlitterTest extends FlatSpec with ChiselScalatestTester with Matchers {
  behavior of "tile data"

  it should "request tile data when the PISO is empty" in {
    test(new TilemapBlitter) { dut =>
      dut.io.config.valid.poke(true.B)
      dut.io.config.ready.expect(true.B)
    }
  }

  it should "request tile data when blitting the last pixel of a 8x8 tile" in {
    test(new TilemapBlitter) { dut =>
      dut.io.config.valid.poke(true.B)
      dut.io.pixelData.valid.poke(true.B)
      dut.clock.step()
      dut.io.config.ready.expect(false.B)
      dut.clock.step(63)
      dut.io.config.ready.expect(true.B)
    }
  }

  it should "request tile data when blitting the last pixel of a 16x16 tile" in {
    test(new TilemapBlitter) { dut =>
      dut.io.config.valid.poke(true.B)
      dut.io.config.bits.layer.tileSize.poke(true.B)
      dut.io.pixelData.valid.poke(true.B)
      dut.clock.step()
      dut.io.config.ready.expect(false.B)
      dut.clock.step(255)
      dut.io.config.ready.expect(true.B)
    }
  }

  behavior of "pixel data"

  it should "request pixel data when the PISO is empty" in {
    test(new TilemapBlitter) { dut =>
      dut.io.pixelData.valid.poke(true.B)
      dut.io.pixelData.ready.expect(true.B)
    }
  }

  it should "request pixel data when the PISO is almost empty" in {
    test(new TilemapBlitter) { dut =>
      dut.io.config.valid.poke(true.B)
      dut.io.pixelData.valid.poke(true.B)
      dut.clock.step()
      dut.io.pixelData.ready.expect(false.B)
      dut.clock.step(7)
      dut.io.pixelData.ready.expect(true.B)
    }
  }

  behavior of "blitting"

  it should "write pixel data to the frame buffer" in {
    test(new TilemapBlitter) { dut =>
      dut.io.config.valid.poke(true.B)
      dut.io.config.bits.layer.scroll.x.poke(509.U)
      dut.io.config.bits.layer.scroll.y.poke(511.U)
      dut.io.pixelData.valid.poke(true.B)
      for (n <- 0 to 7) { dut.io.pixelData.bits(n).poke(1.U) }
      dut.io.paletteRam.dout.poke(1.U)
      dut.clock.step(4)

      // Pixel 0
      dut.io.frameBuffer.wr.expect(true.B)
      dut.io.frameBuffer.addr.expect(0x0.U)
      dut.io.frameBuffer.din.expect(1.U)
      dut.clock.step()

      // Pixel 1
      dut.io.frameBuffer.wr.expect(true.B)
      dut.io.frameBuffer.addr.expect(0x1.U)
      dut.io.frameBuffer.din.expect(1.U)
      dut.clock.step(5)

      // Pixel 6
      dut.io.frameBuffer.wr.expect(true.B)
      dut.io.frameBuffer.addr.expect(0x6.U)
      dut.io.frameBuffer.din.expect(1.U)
      dut.clock.step()

      // Pixel 7
      dut.io.frameBuffer.wr.expect(true.B)
      dut.io.frameBuffer.addr.expect(0x7.U)
      dut.io.frameBuffer.din.expect(1.U)
    }
  }

  it should "assert the busy signal" in {
    test(new TilemapBlitter) { dut =>
      dut.io.config.valid.poke(true.B)
      dut.io.pixelData.valid.poke(true.B)
      dut.clock.step()
      dut.io.config.valid.poke(false.B)
      dut.io.busy.expect(false.B)
      dut.clock.step(2)
      dut.io.busy.expect(true.B)
      dut.clock.step(64)
      dut.io.busy.expect(false.B)
    }
  }
}
