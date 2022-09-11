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

package arcadia.mister

import arcadia.mem._
import chisel3._
import chiseltest._
import org.scalatest._
import flatspec.AnyFlatSpec
import matchers.should.Matchers

class IOCTLTest extends AnyFlatSpec with ChiselScalatestTester with Matchers {
  it should "download ROM data" in {
    test(new Module {
      val io = IO(new Bundle {
        val mem = AsyncWriteMemIO(IOCTL.ADDR_WIDTH, IOCTL.DATA_WIDTH)
        val ioctl = new IOCTL
      })
      io.mem <> io.ioctl.rom
      io.ioctl.wait_n := true.B
      io.ioctl.din := 0.U
    }) { dut =>
      dut.io.mem.wr.expect(false)
      dut.io.ioctl.index.poke(0)
      dut.io.ioctl.download.poke(true)
      dut.io.ioctl.wr.poke(true)
      dut.io.mem.wr.expect(true)
    }
  }

  it should "download NVRAM data" in {
    test(new Module {
      val io = IO(new Bundle {
        val mem = AsyncMemIO(IOCTL.ADDR_WIDTH, IOCTL.DATA_WIDTH)
        val ioctl = new IOCTL
      })
      io.mem <> io.ioctl.nvram
      io.ioctl.wait_n := true.B
      io.ioctl.din := 0.U
    }) { dut =>
      dut.io.mem.wr.expect(false)
      dut.io.ioctl.index.poke(2)
      dut.io.ioctl.download.poke(true)
      dut.io.ioctl.wr.poke(true)
      dut.io.mem.wr.expect(true)
    }
  }

  it should "upload NVRAM data" in {
    test(new Module {
      val io = IO(new Bundle {
        val mem = AsyncMemIO(IOCTL.ADDR_WIDTH, IOCTL.DATA_WIDTH)
        val ioctl = new IOCTL
      })
      io.mem <> io.ioctl.nvram
      io.ioctl.wait_n := true.B
      io.ioctl.din := 0.U
    }) { dut =>
      dut.io.mem.rd.expect(false)
      dut.io.ioctl.index.poke(2)
      dut.io.ioctl.upload.poke(true)
      dut.io.ioctl.rd.poke(true)
      dut.io.mem.rd.expect(true)
    }
  }

  it should "download DIP switch data" in {
    test(new Module {
      val io = IO(new Bundle {
        val mem = AsyncWriteMemIO(IOCTL.ADDR_WIDTH, IOCTL.DATA_WIDTH)
        val ioctl = new IOCTL
      })
      io.mem <> io.ioctl.dips
      io.ioctl.wait_n := true.B
      io.ioctl.din := 0.U
    }) { dut =>
      dut.io.mem.wr.expect(false)
      dut.io.ioctl.index.poke(254)
      dut.io.ioctl.download.poke(true)
      dut.io.ioctl.wr.poke(true)
      dut.io.mem.wr.expect(true)
    }
  }

  it should "download video data" in {
    test(new Module {
      val io = IO(new Bundle {
        val mem = AsyncWriteMemIO(IOCTL.ADDR_WIDTH, IOCTL.DATA_WIDTH)
        val ioctl = new IOCTL
      })
      io.mem <> io.ioctl.video
      io.ioctl.wait_n := true.B
      io.ioctl.din := 0.U
    }) { dut =>
      dut.io.mem.wr.expect(false)
      dut.io.ioctl.index.poke(3)
      dut.io.ioctl.download.poke(true)
      dut.io.ioctl.wr.poke(true)
      dut.io.mem.wr.expect(true)
    }
  }
}
