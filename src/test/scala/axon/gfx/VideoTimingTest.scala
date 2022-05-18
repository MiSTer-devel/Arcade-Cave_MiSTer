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
 * Copyright (c) 2020 Josh Bassett
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package axon.gfx

import chiseltest._
import org.scalatest._
import flatspec.AnyFlatSpec
import matchers.should.Matchers

trait VideoTimingTestHelpers {
  protected val config = VideoTimingConfig(
    clockFreq = 7000000,
    clockDiv = 1,
    hFreq = 15625,
    vFreq = 57.444,
    hDisplay = 320,
    vDisplay = 240,
    hFrontPorch = 36,
    vFrontPorch = 12,
    hRetrace = 20,
    vRetrace = 2
  )

  protected def mkVideoTiming(hInit: Int = 0, vInit: Int = 0) =
    new VideoTiming(config.copy(hInit = hInit, vInit = vInit))
}

class VideoTimingTest extends AnyFlatSpec with ChiselScalatestTester with Matchers with VideoTimingTestHelpers {
  behavior of "x position"

  it should "be incremented" in {
    test(mkVideoTiming(72)) { dut =>
      dut.io.timing.pos.x.expect(0)
      dut.clock.step()
      dut.io.timing.pos.x.expect(1)
    }
  }

  behavior of "horizontal sync"

  it should "be asserted after the horizontal front porch" in {
    test(mkVideoTiming(427)) { dut =>
      dut.io.timing.hSync.expect(false)
      dut.clock.step()
      dut.io.timing.hSync.expect(true)
    }
  }

  it should "be deasserted after the horizontal retrace" in {
    test(mkVideoTiming(447)) { dut =>
      dut.io.timing.hSync.expect(true)
      dut.clock.step()
      dut.io.timing.hSync.expect(false)
    }
  }

  behavior of "horizontal blank"

  it should "be asserted at the end of the horizontal display region" in {
    test(mkVideoTiming(391)) { dut =>
      dut.io.timing.hBlank.expect(false)
      dut.clock.step()
      dut.io.timing.hBlank.expect(true)
    }
  }

  it should "be deasserted at the start of the horizontal display region" in {
    test(mkVideoTiming(71)) { dut =>
      dut.io.timing.hBlank.expect(true)
      dut.clock.step()
      dut.io.timing.hBlank.expect(false)
    }
  }

  behavior of "y position"

  it should "be incremented after the horizontal sync" in {
    test(mkVideoTiming(447, 18)) { dut =>
      dut.io.timing.pos.y.expect(0)
      dut.clock.step()
      dut.io.timing.pos.y.expect(1)
    }
  }

  behavior of "vertical sync"

  it should "be asserted after the vertical front porch" in {
    test(mkVideoTiming(447, 269)) { dut =>
      dut.io.timing.vSync.expect(false)
      dut.clock.step()
      dut.io.timing.vSync.expect(true)
    }
  }

  it should "be deasserted after the vertical retrace" in {
    test(mkVideoTiming(447, 271)) { dut =>
      dut.io.timing.vSync.expect(true)
      dut.clock.step()
      dut.io.timing.vSync.expect(false)
    }
  }

  behavior of "vertical blank"

  it should "be asserted at the end of the vertical display region" in {
    test(mkVideoTiming(447, 257)) { dut =>
      dut.io.timing.vBlank.expect(false)
      dut.clock.step()
      dut.io.timing.vBlank.expect(true)
    }
  }

  it should "be deasserted at the start of the vertical display region" in {
    test(mkVideoTiming(447, 17)) { dut =>
      dut.io.timing.vBlank.expect(true)
      dut.clock.step()
      dut.io.timing.vBlank.expect(false)
    }
  }

  behavior of "display enable"

  it should "be asserted at the start of the horizontal and vertical display regions" in {
    test(mkVideoTiming(71, 18)) { dut =>
      dut.io.timing.displayEnable.expect(false)
      dut.clock.step()
      dut.io.timing.displayEnable.expect(true)
    }
  }

  it should "be deasserted at the end of the horizontal and vertical display regions" in {
    test(mkVideoTiming(391, 257)) { dut =>
      dut.io.timing.displayEnable.expect(true)
      dut.clock.step()
      dut.io.timing.displayEnable.expect(false)
    }
  }
}
