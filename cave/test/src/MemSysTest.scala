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

package cave

import chisel3._
import chiseltest._
import org.scalatest._
import flatspec.AnyFlatSpec
import matchers.should.Matchers

class MemSysTest extends AnyFlatSpec with ChiselScalatestTester with Matchers  {
  it should "write ROM data to DDR memory" in {
    test(new MemSys) { dut =>
      dut.io.prog.rom.wr.poke(true)
      dut.io.prog.rom.addr.poke(0)
      dut.io.prog.rom.din.poke(0x3210)
      dut.io.ddr.wr.expect(false)
      dut.clock.step()
      dut.io.prog.rom.addr.poke(1)
      dut.io.prog.rom.din.poke(0x7654)
      dut.io.ddr.wr.expect(false)
      dut.clock.step()
      dut.io.prog.rom.addr.poke(2)
      dut.io.prog.rom.din.poke(0xba98)
      dut.io.ddr.wr.expect(false)
      dut.clock.step()
      dut.io.prog.rom.addr.poke(3)
      dut.io.prog.rom.din.poke(0xfedc)
      dut.io.ddr.wr.expect(false)
      dut.clock.step()
      dut.io.prog.rom.wr.poke(false)
      dut.io.ddr.wr.expect(true)
      dut.io.ddr.burstLength.expect(1)
      dut.io.ddr.addr.expect(0x30000000)
      dut.io.ddr.din.expect("h_fedcba98_76543210".U)
      dut.clock.step()
    }
  }

  it should "copy ROM data from DDR memory to SDRAM" in {
    test(new MemSys) { dut =>
      dut.io.prog.done.poke(true)
      dut.io.ddr.rd.expect(false)
      dut.io.sdram.wr.expect(false)
      dut.clock.step()
      dut.io.ddr.rd.expect(true)
      dut.io.ddr.burstLength.expect(16)
      dut.io.ddr.addr.expect(0x30000000)
      dut.io.sdram.wr.expect(false)
      dut.clock.step()
      dut.io.ddr.valid.poke(true)
      dut.io.ddr.burstDone.poke(true)
      dut.io.ddr.dout.poke("h_fedcba98_76543210".U)
      dut.clock.step(2)
      dut.io.ddr.valid.poke(false)
      dut.io.ddr.burstDone.poke(false)
      dut.io.sdram.wr.expect(true)
      dut.io.sdram.din.expect(0x3210)
      dut.clock.step()
      dut.io.sdram.wr.expect(true)
      dut.io.sdram.din.expect(0x7654)
      dut.clock.step()
      dut.io.sdram.wr.expect(true)
      dut.io.sdram.din.expect(0xba98)
      dut.clock.step()
      dut.io.sdram.wr.expect(true)
      dut.io.sdram.din.expect(0xfedc)
      dut.clock.step()
    }
  }
}
