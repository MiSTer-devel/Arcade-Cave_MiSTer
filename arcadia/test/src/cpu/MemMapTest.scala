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

package arcadia.cpu

import arcadia.cpu.m68k._
import arcadia.mem._
import chisel3._
import chiseltest._
import org.scalatest._
import flatspec.AnyFlatSpec
import matchers.should.Matchers

class Wrapper extends Module {
  val io = IO(new Bundle {
    val cpu = Flipped(new CPUIO)
    val memA = new AsyncReadMemIO(8, 16)
    val memB = new AsyncReadMemIO(8, 16)
  })

  val map = new MemMap(io.cpu)
  map(0x00 to 0x0f).readMem(io.memA)
  map(0x10 to 0x1f).readMemT(io.memB) { _ ## 0.U }

  // Debug
  if (sys.env.get("DEBUG").contains("1")) {
    printf(p"Wrapper(as: ${io.cpu.as}, dtack: ${io.cpu.dtack})\n")
  }
}

trait MemMapTestHelpers {
  def mkWrapper = {
    val wrapper = new Wrapper
    wrapper.io.cpu.halt := false.B
    wrapper.io.cpu.ipl := 0.U
    wrapper.io.cpu.vpa := 0.U
    wrapper
  }
}

class MemMapTest extends AnyFlatSpec with ChiselScalatestTester with Matchers with MemMapTestHelpers {
  it should "map an async memory device" in {
    test(mkWrapper) { dut =>
      // Start read request
      dut.io.cpu.as.poke(true)
      dut.io.cpu.rw.poke(true)

      // Addr 0x00
      dut.io.cpu.addr.poke(0x0)
      dut.io.memA.rd.expect(true)
      dut.io.memA.addr.expect(0x0)
      dut.io.memB.rd.expect(false)

      // Addr 0x10
      dut.io.cpu.addr.poke(0x8)
      dut.io.memA.rd.expect(false)
      dut.io.memB.rd.expect(true)
      dut.io.memB.addr.expect(0x10)

      // Addr 0x20
      dut.io.cpu.addr.poke(0x10)
      dut.io.memA.rd.expect(false)
      dut.io.memB.rd.expect(false)
    }
  }

  it should "assert DTACK when valid data is available" in {
    test(mkWrapper) { dut =>
      // Start read request
      dut.io.cpu.as.poke(true)
      dut.io.cpu.rw.poke(true)
      dut.io.cpu.dtack.expect(false)
      dut.io.memA.rd.expect(true)
      dut.clock.step()

      // Wait for data
      dut.io.cpu.dtack.expect(false)
      dut.io.memA.rd.expect(false)
      dut.io.memA.valid.poke(true)
      dut.clock.step()

      // Latch data
      dut.io.cpu.dtack.expect(true)
      dut.io.memA.rd.expect(false)
      dut.clock.step()

      // Finish read request
      dut.io.cpu.as.poke(false)
      dut.io.cpu.rw.poke(false)
      dut.io.cpu.dtack.expect(true)
      dut.io.memA.rd.expect(false)
      dut.clock.step()

      // All done
      dut.io.cpu.dtack.expect(false)
      dut.io.memA.rd.expect(false)
      dut.clock.step()
    }
  }
}
