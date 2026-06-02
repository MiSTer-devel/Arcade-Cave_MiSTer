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

import chisel3.simulator.scalatest.ChiselSim
import org.scalatest._
import flatspec.AnyFlatSpec
import matchers.should.Matchers

class SpriteBlitterTest extends AnyFlatSpec with ChiselSim with Matchers {
  behavior of "sprite data"

  it should "request sprite data when the PISO is empty" in {
    simulate(new SpriteBlitter) { dut =>
      dut.io.config.valid.poke(true)
      dut.io.config.ready.expect(true)
    }
  }

  it should "request sprite data when blitting the last pixel" in {
    simulate(new SpriteBlitter) { dut =>
      dut.io.enable.poke(true)
      dut.io.config.valid.poke(true)
      dut.io.config.bits.sprite.cols.poke(1)
      dut.io.config.bits.sprite.rows.poke(1)
      dut.io.config.bits.sprite.zoom.x.poke(0x100)
      dut.io.config.bits.sprite.zoom.y.poke(0x100)
      dut.io.frameBuffer.wait_n.poke(true)
      dut.io.pixelData.valid.poke(true)
      dut.clock.step()
      dut.io.config.ready.expect(false)
      dut.clock.step(255)
      dut.io.config.ready.expect(true)
    }
  }

  behavior of "pixel data"

  it should "request pixel data when the PISO is empty" in {
    simulate(new SpriteBlitter) { dut =>
      dut.io.frameBuffer.wait_n.poke(true)
      dut.io.pixelData.valid.poke(true)
      dut.io.pixelData.ready.expect(true)
    }
  }

  it should "request pixel data when the PISO is almost empty" in {
    simulate(new SpriteBlitter) { dut =>
      dut.io.enable.poke(true)
      dut.io.config.valid.poke(true)
      dut.io.config.bits.sprite.cols.poke(1)
      dut.io.config.bits.sprite.rows.poke(1)
      dut.io.config.bits.sprite.zoom.x.poke(0x100)
      dut.io.config.bits.sprite.zoom.y.poke(0x100)
      dut.io.frameBuffer.wait_n.poke(true)
      dut.io.pixelData.valid.poke(true)
      dut.clock.step()
      dut.io.pixelData.ready.expect(false)
      dut.clock.step(15)
      dut.io.pixelData.ready.expect(true)
    }
  }

  behavior of "blitting"

  it should "assert the busy signal" in {
    simulate(new SpriteBlitter) { dut =>
      dut.io.enable.poke(true)
      dut.io.config.valid.poke(true)
      dut.io.config.bits.sprite.cols.poke(1)
      dut.io.config.bits.sprite.rows.poke(1)
      dut.io.config.bits.sprite.zoom.x.poke(0x100)
      dut.io.config.bits.sprite.zoom.y.poke(0x100)
      dut.io.frameBuffer.wait_n.poke(true)
      dut.io.pixelData.valid.poke(true)
      dut.io.busy.expect(false)
      dut.clock.step()
      dut.io.config.valid.poke(false)
      dut.io.busy.expect(true)
      dut.clock.step(256)
      dut.io.busy.expect(false)
    }
  }

  it should "write pixel data to the frame buffer" in {
    simulate(new SpriteBlitter) { dut =>
      dut.io.enable.poke(true)
      dut.io.config.valid.poke(true)
      dut.io.config.bits.sprite.cols.poke(2)
      dut.io.config.bits.sprite.rows.poke(1)
      dut.io.config.bits.sprite.pos.x.poke(0x1000)
      dut.io.config.bits.sprite.pos.y.poke(0x1000)
      dut.io.config.bits.sprite.zoom.x.poke(0x100)
      dut.io.config.bits.sprite.zoom.y.poke(0x100)
      dut.io.frameBuffer.wait_n.poke(true)
      dut.io.pixelData.valid.poke(true)
      for (n <- 0 to 15) { dut.io.pixelData.bits(n).poke(1) }
      dut.clock.step(2)
      dut.io.config.valid.poke(false)
      dut.io.pixelData.valid.poke(false)

      // pixel 0
      dut.io.frameBuffer.wr.expect(true)
      dut.io.frameBuffer.addr.expect(0x2010)
      dut.io.frameBuffer.din.expect(1)
      dut.io.frameBuffer.mask.expect(3)
      dut.clock.step()

      // pixel 1
      dut.io.frameBuffer.wr.expect(true)
      dut.io.frameBuffer.addr.expect(0x2011)
      dut.io.frameBuffer.din.expect(1)
      dut.clock.step(13)

      // pixel 14 (request pixel data)
      dut.io.pixelData.ready.expect(true)
      dut.io.pixelData.valid.poke(true)
      for (n <- 0 to 15) { dut.io.pixelData.bits(n).poke(2) }
      dut.io.frameBuffer.wr.expect(true)
      dut.io.frameBuffer.addr.expect(0x201e)
      dut.io.frameBuffer.din.expect(1)
      dut.clock.step()

      // pixel 15
      dut.io.pixelData.valid.poke(false)
      dut.io.frameBuffer.wr.expect(true)
      dut.io.frameBuffer.addr.expect(0x201f)
      dut.io.frameBuffer.din.expect(1)
      dut.clock.step()

      // pixel 16
      dut.io.frameBuffer.wr.expect(true)
      dut.io.frameBuffer.addr.expect(0x2020)
      dut.io.frameBuffer.din.expect(2)
      dut.clock.step()

      // pixel 17
      dut.io.frameBuffer.wr.expect(true)
      dut.io.frameBuffer.addr.expect(0x2021)
      dut.io.frameBuffer.din.expect(2)
      dut.clock.step(13)

      // pixel 30
      dut.io.frameBuffer.wr.expect(true)
      dut.io.frameBuffer.addr.expect(0x202e)
      dut.io.frameBuffer.din.expect(2)
      dut.clock.step()

      // pixel 31
      dut.io.frameBuffer.wr.expect(true)
      dut.io.frameBuffer.addr.expect(0x202f)
      dut.io.frameBuffer.din.expect(2)
    }
  }

  it should "wait for the frame buffer" in {
    simulate(new SpriteBlitter) { dut =>
      dut.io.enable.poke(true)
      dut.io.config.valid.poke(true)
      dut.io.config.bits.sprite.cols.poke(1)
      dut.io.config.bits.sprite.rows.poke(1)
      dut.io.config.bits.sprite.zoom.x.poke(0x100)
      dut.io.config.bits.sprite.zoom.y.poke(0x100)
      dut.io.frameBuffer.wait_n.poke(true)
      dut.io.pixelData.valid.poke(true)
      for (n <- 0 to 15) { dut.io.pixelData.bits(n).poke(1) }
      dut.clock.step(2)
      dut.io.config.valid.poke(false)
      dut.io.pixelData.valid.poke(false)

      // wait
      dut.io.frameBuffer.wait_n.poke(false)
      dut.io.frameBuffer.wr.expect(true)
      dut.io.frameBuffer.addr.expect(0x0)
      dut.clock.step()

      // pixel 0
      dut.io.frameBuffer.wait_n.poke(true)
      dut.io.frameBuffer.wr.expect(true)
      dut.io.frameBuffer.addr.expect(0x0)
      dut.clock.step()

      // pixel 1
      dut.io.frameBuffer.wr.expect(true)
      dut.io.frameBuffer.addr.expect(0x1)
      dut.clock.step(13)

      // pixel 14
      dut.io.frameBuffer.wr.expect(true)
      dut.io.frameBuffer.addr.expect(0xe)
      dut.clock.step()

      // wait
      dut.io.frameBuffer.wait_n.poke(false)
      dut.io.frameBuffer.wr.expect(true)
      dut.io.frameBuffer.addr.expect(0xf)
      dut.clock.step()

      // pixel 15
      dut.io.frameBuffer.wait_n.poke(true)
      dut.io.frameBuffer.wr.expect(true)
      dut.io.frameBuffer.addr.expect(0xf)
      dut.clock.step()
    }
  }

  it should "handle horizontal flipping" in {
    simulate(new SpriteBlitter) { dut =>
      dut.io.enable.poke(true)
      dut.io.config.valid.poke(true)
      dut.io.config.bits.sprite.cols.poke(1)
      dut.io.config.bits.sprite.rows.poke(1)
      dut.io.config.bits.sprite.zoom.x.poke(0x100)
      dut.io.config.bits.sprite.zoom.y.poke(0x100)
      dut.io.config.bits.sprite.hFlip.poke(true)
      dut.io.frameBuffer.wait_n.poke(true)
      dut.io.pixelData.valid.poke(true)
      for (n <- 0 to 15) { dut.io.pixelData.bits(n).poke(1) }
      dut.clock.step(2)
      dut.io.config.valid.poke(false)
      dut.io.pixelData.valid.poke(false)

      // pixel 0
      dut.io.frameBuffer.wr.expect(true)
      dut.io.frameBuffer.addr.expect(0xf)
      dut.io.frameBuffer.din.expect(1)
      dut.io.frameBuffer.mask.expect(3)
      dut.clock.step()

      // pixel 1
      dut.io.frameBuffer.addr.expect(0xe)
      dut.io.frameBuffer.din.expect(1)
      dut.clock.step(13)

      // pixel 14
      dut.io.frameBuffer.addr.expect(0x1)
      dut.io.frameBuffer.din.expect(1)
      dut.clock.step()

      // pixel 15
      dut.io.frameBuffer.addr.expect(0x0)
      dut.io.frameBuffer.din.expect(1)
      dut.clock.step()
    }
  }

  it should "handle vertical flipping" in {
    simulate(new SpriteBlitter) { dut =>
      dut.io.enable.poke(true)
      dut.io.config.valid.poke(true)
      dut.io.config.bits.sprite.cols.poke(1)
      dut.io.config.bits.sprite.rows.poke(1)
      dut.io.config.bits.sprite.zoom.x.poke(0x100)
      dut.io.config.bits.sprite.zoom.y.poke(0x100)
      dut.io.config.bits.sprite.vFlip.poke(true)
      dut.io.frameBuffer.wait_n.poke(true)
      dut.io.pixelData.valid.poke(true)
      for (n <- 0 to 15) { dut.io.pixelData.bits(n).poke(1) }
      dut.clock.step(2)
      dut.io.config.valid.poke(false)
      dut.io.pixelData.valid.poke(false)

      // pixel 0
      dut.io.frameBuffer.wr.expect(true)
      dut.io.frameBuffer.addr.expect(0x1e00)
      dut.io.frameBuffer.din.expect(1)
      dut.io.frameBuffer.mask.expect(3)
      dut.clock.step()

      // pixel 1
      dut.io.frameBuffer.addr.expect(0x1e01)
      dut.io.frameBuffer.din.expect(1)
      dut.clock.step(13)

      // pixel 14
      dut.io.frameBuffer.addr.expect(0x1e0e)
      dut.io.frameBuffer.din.expect(1)
      dut.clock.step()

      // pixel 15
      dut.io.frameBuffer.addr.expect(0x1e0f)
      dut.io.frameBuffer.din.expect(1)
      dut.clock.step()
    }
  }

  behavior of "downscaling"

  it should "write pixel data to the frame buffer" in {
    simulate(new SpriteBlitter) { dut =>
      dut.io.enable.poke(true)
      dut.io.config.valid.poke(true)
      dut.io.config.bits.sprite.cols.poke(1)
      dut.io.config.bits.sprite.rows.poke(1)
      dut.io.config.bits.sprite.zoom.x.poke(0x80) // 0.5x scaling
      dut.io.config.bits.sprite.zoom.y.poke(0x100)
      dut.io.frameBuffer.wait_n.poke(true)
      dut.io.pixelData.valid.poke(true)
      for (n <- 0 to 15) { dut.io.pixelData.bits(n).poke(1) }
      dut.clock.step(2)
      dut.io.config.valid.poke(false)
      dut.io.pixelData.valid.poke(false)

      // pixel 0
      dut.io.frameBuffer.wr.expect(true)
      dut.io.frameBuffer.addr.expect(0x0)
      dut.io.frameBuffer.din.expect(1)
      dut.io.frameBuffer.mask.expect(3)
      dut.clock.step()

      // pixel 1
      dut.io.frameBuffer.wr.expect(true)
      dut.io.frameBuffer.addr.expect(0x0)
      dut.io.frameBuffer.din.expect(1)
      dut.clock.step(13)

      // pixel 14
      dut.io.frameBuffer.wr.expect(true)
      dut.io.frameBuffer.addr.expect(0x7)
      dut.io.frameBuffer.din.expect(1)
      dut.clock.step()

      // pixel 15
      dut.io.frameBuffer.wr.expect(true)
      dut.io.frameBuffer.addr.expect(0x7)
      dut.io.frameBuffer.din.expect(1)
      dut.clock.step()
    }
  }

  it should "handle horizontal flipping" in {
    simulate(new SpriteBlitter) { dut =>
      dut.io.enable.poke(true)
      dut.io.config.valid.poke(true)
      dut.io.config.bits.sprite.cols.poke(1)
      dut.io.config.bits.sprite.rows.poke(1)
      dut.io.config.bits.sprite.zoom.x.poke(0x80) // 0.5x scaling
      dut.io.config.bits.sprite.zoom.y.poke(0x100)
      dut.io.config.bits.sprite.hFlip.poke(true)
      dut.io.frameBuffer.wait_n.poke(true)
      dut.io.pixelData.valid.poke(true)
      for (n <- 0 to 15) { dut.io.pixelData.bits(n).poke(1) }
      dut.clock.step(2)
      dut.io.config.valid.poke(false)
      dut.io.pixelData.valid.poke(false)

      // pixel 0
      dut.io.frameBuffer.wr.expect(true)
      dut.io.frameBuffer.addr.expect(0xf)
      dut.io.frameBuffer.din.expect(1)
      dut.io.frameBuffer.mask.expect(3)
      dut.clock.step()

      // pixel 1
      dut.io.frameBuffer.addr.expect(0xe)
      dut.io.frameBuffer.din.expect(1)
      dut.clock.step(13)

      // pixel 14
      dut.io.frameBuffer.addr.expect(0x8)
      dut.io.frameBuffer.din.expect(1)
      dut.clock.step()

      // pixel 15
      dut.io.frameBuffer.addr.expect(0x7)
      dut.io.frameBuffer.din.expect(1)
      dut.clock.step()
    }
  }

  behavior of "upscaling"

  it should "write pixel data to the frame buffer" in {
    simulate(new SpriteBlitter) { dut =>
      dut.io.enable.poke(true)
      dut.io.config.valid.poke(true)
      dut.io.config.bits.sprite.cols.poke(1)
      dut.io.config.bits.sprite.rows.poke(1)
      dut.io.config.bits.sprite.zoom.x.poke(0x200) // 2x scaling
      dut.io.config.bits.sprite.zoom.y.poke(0x100)
      dut.io.frameBuffer.wait_n.poke(true)
      dut.io.pixelData.valid.poke(true)
      for (n <- 0 to 15) { dut.io.pixelData.bits(n).poke(1) }
      dut.clock.step(2)
      dut.io.config.valid.poke(false)
      dut.io.pixelData.valid.poke(false)

      // pixel 0
      dut.io.frameBuffer.wr.expect(true)
      dut.io.frameBuffer.addr.expect(0x00)
      dut.io.frameBuffer.din.expect(1)
      dut.io.frameBuffer.mask.expect(3)
      dut.clock.step()

      // pixel 1
      dut.io.frameBuffer.wr.expect(true)
      dut.io.frameBuffer.addr.expect(0x02)
      dut.io.frameBuffer.din.expect(1)
      dut.clock.step(13)

      // pixel 14
      dut.io.frameBuffer.wr.expect(true)
      dut.io.frameBuffer.addr.expect(0x1c)
      dut.io.frameBuffer.din.expect(1)
      dut.clock.step()

      // pixel 15
      dut.io.frameBuffer.wr.expect(true)
      dut.io.frameBuffer.addr.expect(0x1e)
      dut.io.frameBuffer.din.expect(1)
      dut.clock.step()
    }
  }

  it should "handle horizontal flipping" in {
    simulate(new SpriteBlitter) { dut =>
      dut.io.enable.poke(true)
      dut.io.config.valid.poke(true)
      dut.io.config.bits.sprite.cols.poke(1)
      dut.io.config.bits.sprite.rows.poke(1)
      dut.io.config.bits.sprite.zoom.x.poke(0x200) // 2x scaling
      dut.io.config.bits.sprite.zoom.y.poke(0x100)
      dut.io.config.bits.sprite.hFlip.poke(true)
      dut.io.frameBuffer.wait_n.poke(true)
      dut.io.pixelData.valid.poke(true)
      for (n <- 0 to 15) { dut.io.pixelData.bits(n).poke(1) }
      dut.clock.step(2)
      dut.io.config.valid.poke(false)
      dut.io.pixelData.valid.poke(false)

      // pixel 0
      dut.io.frameBuffer.wr.expect(true)
      dut.io.frameBuffer.addr.expect(0x0000f)
      dut.io.frameBuffer.din.expect(1)
      dut.io.frameBuffer.mask.expect(3)
      dut.clock.step()

      // pixel 1
      dut.io.frameBuffer.addr.expect(0x0000d)
      dut.io.frameBuffer.din.expect(1)
      dut.clock.step(13)

      // pixel 14
      dut.io.frameBuffer.addr.expect(0x1fff3)
      dut.io.frameBuffer.din.expect(1)
      dut.clock.step()

      // pixel 15
      dut.io.frameBuffer.addr.expect(0x1fff1)
      dut.io.frameBuffer.din.expect(1)
      dut.clock.step()
    }
  }
}
