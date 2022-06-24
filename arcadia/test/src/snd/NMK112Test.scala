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

package arcadia.snd

import chisel3._
import chiseltest._
import org.scalatest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class NMK112Test extends AnyFlatSpec with ChiselScalatestTester with Matchers {
  it should "map ROM addresses" in {
    test(new NMK112) { dut =>
      dut.io.cpu.wr.poke(true.B)

      // write (chip 0, bank 0)
      dut.io.cpu.addr.poke(0.U)
      dut.io.cpu.din.poke(0.U)
      dut.clock.step()

      // write (chip 0, bank 1)
      dut.io.cpu.addr.poke(1.U)
      dut.io.cpu.din.poke(1.U)
      dut.clock.step()

      // write (chip 1, bank 0)
      dut.io.cpu.addr.poke(4.U)
      dut.io.cpu.din.poke(4.U)
      dut.clock.step()

      // write (chip 1, bank 1)
      dut.io.cpu.addr.poke(5.U)
      dut.io.cpu.din.poke(5.U)
      dut.clock.step()

      // phrase table (chip 0, bank 0)
      dut.io.addr(0).in.poke(0x00000)
      dut.io.addr(0).out.expect(0x00000)
      dut.io.addr(0).in.poke(0x000ff)
      dut.io.addr(0).out.expect(0x000ff)

      // phrase table (chip 0, bank 1)
      dut.io.addr(0).in.poke(0x00100)
      dut.io.addr(0).out.expect(0x10100)
      dut.io.addr(0).in.poke(0x001ff)
      dut.io.addr(0).out.expect(0x101ff)

      // phrase table (chip 1, bank 0)
      dut.io.addr(1).in.poke(0x00000)
      dut.io.addr(1).out.expect(0x40000)
      dut.io.addr(1).in.poke(0x000ff)
      dut.io.addr(1).out.expect(0x400ff)

      // phrase table (chip 1, bank 1)
      dut.io.addr(1).in.poke(0x00100)
      dut.io.addr(1).out.expect(0x50100)
      dut.io.addr(1).in.poke(0x001ff)
      dut.io.addr(1).out.expect(0x501ff)

      // ADPCM data (chip 0, bank 0)
      dut.io.addr(0).in.poke(0x00400)
      dut.io.addr(0).out.expect(0x00400)
      dut.io.addr(0).in.poke(0x0ffff)
      dut.io.addr(0).out.expect(0x0ffff)

      // ADPCM data (chip 0, bank 1)
      dut.io.addr(0).in.poke(0x10400)
      dut.io.addr(0).out.expect(0x10400)
      dut.io.addr(0).in.poke(0x1ffff)
      dut.io.addr(0).out.expect(0x1ffff)

      // ADPCM data (chip 1, bank 0)
      dut.io.addr(1).in.poke(0x00400)
      dut.io.addr(1).out.expect(0x40400)
      dut.io.addr(1).in.poke(0x0ffff)
      dut.io.addr(1).out.expect(0x4ffff)

      // ADPCM data (chip 1, bank 1)
      dut.io.addr(1).in.poke(0x10400)
      dut.io.addr(1).out.expect(0x50400)
      dut.io.addr(1).in.poke(0x1ffff)
      dut.io.addr(1).out.expect(0x5ffff)
    }
  }

  it should "map ROM addresses with phrase table masking" in {
    test(new NMK112) { dut =>
      dut.io.cpu.wr.poke(true.B)
      dut.io.mask.poke(1.U)

      // write (chip 0, bank 0)
      dut.io.cpu.addr.poke(0.U)
      dut.io.cpu.din.poke(0.U)
      dut.clock.step()

      // write (chip 0, bank 1)
      dut.io.cpu.addr.poke(1.U)
      dut.io.cpu.din.poke(1.U)
      dut.clock.step()

      // write (chip 1, bank 0)
      dut.io.cpu.addr.poke(4.U)
      dut.io.cpu.din.poke(4.U)
      dut.clock.step()

      // write (chip 1, bank 1)
      dut.io.cpu.addr.poke(5.U)
      dut.io.cpu.din.poke(5.U)
      dut.clock.step()

      // phrase table (chip 0, bank 0)
      dut.io.addr(0).in.poke(0x00000)
      dut.io.addr(0).out.expect(0x00000)
      dut.io.addr(0).in.poke(0x000ff)
      dut.io.addr(0).out.expect(0x000ff)

      // phrase table (chip 0, bank 1)
      dut.io.addr(0).in.poke(0x00100)
      dut.io.addr(0).out.expect(0x00100)
      dut.io.addr(0).in.poke(0x001ff)
      dut.io.addr(0).out.expect(0x001ff)

      // phrase table (chip 1, bank 0)
      dut.io.addr(1).in.poke(0x00000)
      dut.io.addr(1).out.expect(0x40000)
      dut.io.addr(1).in.poke(0x000ff)
      dut.io.addr(1).out.expect(0x400ff)

      // phrase table (chip 1, bank 1)
      dut.io.addr(1).in.poke(0x00100)
      dut.io.addr(1).out.expect(0x50100)
      dut.io.addr(1).in.poke(0x001ff)
      dut.io.addr(1).out.expect(0x501ff)

      // ADPCM data (chip 0, bank 0)
      dut.io.addr(0).in.poke(0x00400)
      dut.io.addr(0).out.expect(0x00400)
      dut.io.addr(0).in.poke(0x0ffff)
      dut.io.addr(0).out.expect(0x0ffff)

      // ADPCM data (chip 0, bank 1)
      dut.io.addr(0).in.poke(0x10400)
      dut.io.addr(0).out.expect(0x10400)
      dut.io.addr(0).in.poke(0x1ffff)
      dut.io.addr(0).out.expect(0x1ffff)

      // ADPCM data (chip 1, bank 0)
      dut.io.addr(1).in.poke(0x00400)
      dut.io.addr(1).out.expect(0x40400)
      dut.io.addr(1).in.poke(0x0ffff)
      dut.io.addr(1).out.expect(0x4ffff)

      // ADPCM data (chip 1, bank 1)
      dut.io.addr(1).in.poke(0x10400)
      dut.io.addr(1).out.expect(0x50400)
      dut.io.addr(1).in.poke(0x1ffff)
      dut.io.addr(1).out.expect(0x5ffff)
    }
  }
}
