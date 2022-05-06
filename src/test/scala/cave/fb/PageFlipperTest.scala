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

package cave.fb

import chiseltest._
import org.scalatest._
import flatspec.AnyFlatSpec
import matchers.should.Matchers

class PageFlipperTest extends AnyFlatSpec with ChiselScalatestTester with Matchers {
  it should "flip pages in double buffer mode" in {
    test(new PageFlipper(0x24000000)) { dut =>
      dut.io.mode.poke(false)
      dut.io.addrRead.expect(0x24000000)
      dut.io.addrWrite.expect(0x24080000)

      // Swap read
      dut.io.swapRead.poke(true)
      dut.clock.step()
      dut.io.addrRead.expect(0x24000000)
      dut.io.addrWrite.expect(0x24080000)

      // Swap write
      dut.io.swapRead.poke(false)
      dut.io.swapWrite.poke(true)
      dut.clock.step()
      dut.io.addrRead.expect(0x24080000)
      dut.io.addrWrite.expect(0x24000000)

      // Swap read and write
      dut.io.swapRead.poke(true)
      dut.io.swapWrite.poke(true)
      dut.clock.step()
      dut.io.addrRead.expect(0x24000000)
      dut.io.addrWrite.expect(0x24080000)
    }
  }

  it should "flip pages in triple buffer mode" in {
    test(new PageFlipper(0x24000000)) { dut =>
      dut.io.mode.poke(true)
      dut.io.addrRead.expect(0x24000000)
      dut.io.addrWrite.expect(0x24080000)

      // Swap read
      dut.io.swapRead.poke(true)
      dut.clock.step()
      dut.io.addrRead.expect(0x24100000)
      dut.io.addrWrite.expect(0x24080000)

      // Swap write
      dut.io.swapRead.poke(false)
      dut.io.swapWrite.poke(true)
      dut.clock.step()
      dut.io.addrRead.expect(0x24100000)
      dut.io.addrWrite.expect(0x24000000)

      // Swap read and write
      dut.io.swapRead.poke(true)
      dut.io.swapWrite.poke(true)
      dut.clock.step()
      dut.io.addrRead.expect(0x24000000)
      dut.io.addrWrite.expect(0x24080000)
    }
  }
}
