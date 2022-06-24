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

package arcadia.gfx

import chiseltest._
import org.scalatest._
import flatspec.AnyFlatSpec
import matchers.should.Matchers

trait VideoTimingTestHelpers {
  protected val config = VideoTimingConfig(
    clockFreq = 7000000,
    clockDiv = 1,
    hFreq = 15625,
    vFreq = 57.444
  )

  protected def mkVideoTiming(hInit: Int = 0, vInit: Int = 0) =
    new VideoTiming(config.copy(hInit = hInit, vInit = vInit))

  protected def initTiming(dut: VideoTiming) = {
    dut.io.display.x.poke(320)
    dut.io.display.y.poke(240)
    dut.io.frontPorch.x.poke(36)
    dut.io.frontPorch.y.poke(12)
    dut.io.retrace.x.poke(20)
    dut.io.retrace.y.poke(2)
  }
}

class VideoTimingTest extends AnyFlatSpec with ChiselScalatestTester with Matchers with VideoTimingTestHelpers {
  behavior of "x position"

  it should "be incremented" in {
    test(mkVideoTiming(72)) { dut =>
      initTiming(dut)
      dut.io.timing.pos.x.expect(0)
      dut.clock.step()
      dut.io.timing.pos.x.expect(1)
    }
  }

  behavior of "horizontal sync"

  it should "be asserted after the horizontal front porch" in {
    test(mkVideoTiming(427)) { dut =>
      initTiming(dut)
      dut.io.timing.hSync.expect(false)
      dut.clock.step()
      dut.io.timing.hSync.expect(true)
    }
  }

  it should "be deasserted after the horizontal retrace" in {
    test(mkVideoTiming(447)) { dut =>
      initTiming(dut)
      dut.io.timing.hSync.expect(true)
      dut.clock.step()
      dut.io.timing.hSync.expect(false)
    }
  }

  behavior of "horizontal blank"

  it should "be asserted at the end of the horizontal display region" in {
    test(mkVideoTiming(391)) { dut =>
      initTiming(dut)
      dut.io.timing.hBlank.expect(false)
      dut.clock.step()
      dut.io.timing.hBlank.expect(true)
    }
  }

  it should "be deasserted at the start of the horizontal display region" in {
    test(mkVideoTiming(71)) { dut =>
      initTiming(dut)
      dut.io.timing.hBlank.expect(true)
      dut.clock.step()
      dut.io.timing.hBlank.expect(false)
    }
  }

  behavior of "y position"

  it should "be incremented after the horizontal sync" in {
    test(mkVideoTiming(447, 18)) { dut =>
      initTiming(dut)
      dut.io.timing.pos.y.expect(0)
      dut.clock.step()
      dut.io.timing.pos.y.expect(1)
    }
  }

  behavior of "vertical sync"

  it should "be asserted after the vertical front porch" in {
    test(mkVideoTiming(447, 269)) { dut =>
      initTiming(dut)
      dut.io.timing.vSync.expect(false)
      dut.clock.step()
      dut.io.timing.vSync.expect(true)
    }
  }

  it should "be deasserted after the vertical retrace" in {
    test(mkVideoTiming(447, 271)) { dut =>
      initTiming(dut)
      dut.io.timing.vSync.expect(true)
      dut.clock.step()
      dut.io.timing.vSync.expect(false)
    }
  }

  behavior of "vertical blank"

  it should "be asserted at the end of the vertical display region" in {
    test(mkVideoTiming(447, 257)) { dut =>
      initTiming(dut)
      dut.io.timing.vBlank.expect(false)
      dut.clock.step()
      dut.io.timing.vBlank.expect(true)
    }
  }

  it should "be deasserted at the start of the vertical display region" in {
    test(mkVideoTiming(447, 17)) { dut =>
      initTiming(dut)
      dut.io.timing.vBlank.expect(true)
      dut.clock.step()
      dut.io.timing.vBlank.expect(false)
    }
  }

  behavior of "display enable"

  it should "be asserted at the start of the horizontal and vertical display regions" in {
    test(mkVideoTiming(71, 18)) { dut =>
      initTiming(dut)
      dut.io.timing.displayEnable.expect(false)
      dut.clock.step()
      dut.io.timing.displayEnable.expect(true)
    }
  }

  it should "be deasserted at the end of the horizontal and vertical display regions" in {
    test(mkVideoTiming(391, 257)) { dut =>
      initTiming(dut)
      dut.io.timing.displayEnable.expect(true)
      dut.clock.step()
      dut.io.timing.displayEnable.expect(false)
    }
  }
}
