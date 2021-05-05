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

class SpriteBlitterTest extends FlatSpec with ChiselScalatestTester with Matchers {
  it should "read palette data" in {
    test(new SpriteBlitter) { dut =>
      dut.io.paletteRam.rd.expect(true.B)
    }
  }

  it should "not read priority data" in {
    test(new SpriteBlitter) { dut =>
      dut.io.priority.read.rd.expect(false.B)
    }
  }

  it should "decode tile data" in {
    test(new SpriteBlitter) { dut =>
      dut.io.sprite.valid.poke(true.B)
      dut.io.sprite.bits.colorCode.poke(1.U)
      dut.io.pixelData.valid.poke(true.B)
      dut.io.pixelData.bits.poke("hfedcba9876543210".U)
      dut.clock.step()
      for (n <- Seq(14, 15, 12, 13, 10, 11, 8, 9, 6, 7, 4, 5, 2, 3, 0, 1)) {
        dut.clock.step()
        dut.io.paletteRam.addr.expect((0x100 + n).U)
      }
    }
  }

  behavior of "blitting"

  it should "copy pixel data to the frame buffer" in {
    test(new SpriteBlitter) { dut =>
      dut.io.sprite.valid.poke(true.B)
      dut.io.sprite.bits.cols.poke(1.U)
      dut.io.sprite.bits.rows.poke(1.U)
      dut.io.pixelData.valid.poke(true.B)
      dut.io.pixelData.bits.poke("h1111111111111111".U)
      dut.io.paletteRam.dout.poke(1.U)
      dut.clock.step(4)

      // Pixel 0
      dut.io.frameBuffer.wr.expect(true.B)
      dut.io.frameBuffer.addr.expect(0x0000.U)
      dut.io.frameBuffer.din.expect(1.U)
      dut.clock.step()

      // Pixel 1
      dut.io.frameBuffer.addr.expect(0x0001.U)
      dut.io.frameBuffer.din.expect(1.U)
      dut.clock.step(13)

      // Pixel 14
      dut.io.frameBuffer.addr.expect(0x000e.U)
      dut.io.frameBuffer.din.expect(1.U)
      dut.clock.step()

      // Pixel 15
      dut.io.frameBuffer.addr.expect(0x000f.U)
      dut.io.frameBuffer.din.expect(1.U)
      dut.clock.step()
    }
  }

  it should "allow horizontal flipping" in {
    test(new SpriteBlitter) { dut =>
      dut.io.sprite.valid.poke(true.B)
      dut.io.sprite.bits.cols.poke(1.U)
      dut.io.sprite.bits.rows.poke(1.U)
      dut.io.sprite.bits.flipX.poke(true.B)
      dut.io.pixelData.valid.poke(true.B)
      dut.io.pixelData.bits.poke("h1111111111111111".U)
      dut.io.paletteRam.dout.poke(1.U)
      dut.clock.step(4)

      // Pixel 0
      dut.io.frameBuffer.wr.expect(true.B)
      dut.io.frameBuffer.addr.expect(0x000f.U)
      dut.io.frameBuffer.din.expect(1.U)
      dut.clock.step()

      // Pixel 1
      dut.io.frameBuffer.addr.expect(0x000e.U)
      dut.io.frameBuffer.din.expect(1.U)
      dut.clock.step(13)

      // Pixel 14
      dut.io.frameBuffer.addr.expect(0x0001.U)
      dut.io.frameBuffer.din.expect(1.U)
      dut.clock.step()

      // Pixel 15
      dut.io.frameBuffer.addr.expect(0x0000.U)
      dut.io.frameBuffer.din.expect(1.U)
      dut.clock.step()
    }
  }

  it should "allow vertical flipping" in {
    test(new SpriteBlitter) { dut =>
      dut.io.sprite.valid.poke(true.B)
      dut.io.sprite.bits.cols.poke(1.U)
      dut.io.sprite.bits.rows.poke(1.U)
      dut.io.sprite.bits.flipY.poke(true.B)
      dut.io.pixelData.valid.poke(true.B)
      dut.io.pixelData.bits.poke("h1111111111111111".U)
      dut.io.paletteRam.dout.poke(1.U)
      dut.clock.step(4)

      // Pixel 0
      dut.io.frameBuffer.wr.expect(true.B)
      dut.io.frameBuffer.addr.expect(0x12c0.U)
      dut.io.frameBuffer.din.expect(1.U)
      dut.clock.step()

      // Pixel 1
      dut.io.frameBuffer.addr.expect(0x12c1.U)
      dut.io.frameBuffer.din.expect(1.U)
      dut.clock.step(13)

      // Pixel 14
      dut.io.frameBuffer.addr.expect(0x12ce.U)
      dut.io.frameBuffer.din.expect(1.U)
      dut.clock.step()

      // Pixel 15
      dut.io.frameBuffer.addr.expect(0x12cf.U)
      dut.io.frameBuffer.din.expect(1.U)
      dut.clock.step()
    }
  }
}
