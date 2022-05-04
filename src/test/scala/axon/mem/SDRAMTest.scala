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

package axon.mem

import axon.mem.sdram.{SDRAM, Config}
import chiseltest._
import org.scalatest._
import flatspec.AnyFlatSpec
import matchers.should.Matchers

trait SDRAMTestHelpers {
  protected val sdramConfig = Config(
    clockFreq = 100000000,
    tINIT = 20,
    tMRD = 10,
    tRC = 20,
    tRCD = 20,
    tRP = 10,
    tWR = 10,
    tREFI = 100
  )

  protected def mkSDRAM(config: Config = sdramConfig) = new SDRAM(config)

  protected def waitForInit(dut: SDRAM) =
    while (!dut.io.debug.init.peekBoolean()) { dut.clock.step() }

  protected def waitForMode(dut: SDRAM) =
    while (!dut.io.debug.mode.peekBoolean()) { dut.clock.step() }

  protected def waitForIdle(dut: SDRAM) =
    while (!dut.io.debug.idle.peekBoolean()) { dut.clock.step() }

  protected def waitForActive(dut: SDRAM) =
    while (!dut.io.debug.active.peekBoolean()) { dut.clock.step() }

  protected def waitForRead(dut: SDRAM) =
    while (!dut.io.debug.read.peekBoolean()) { dut.clock.step() }

  protected def waitForWrite(dut: SDRAM) =
    while (!dut.io.debug.write.peekBoolean()) { dut.clock.step() }

  protected def waitForRefresh(dut: SDRAM) =
    while (!dut.io.debug.refresh.peekBoolean()) { dut.clock.step() }
}

class SDRAMTest extends AnyFlatSpec with ChiselScalatestTester with Matchers with SDRAMTestHelpers {
  behavior of "FSM"

  it should "move to the mode state after initializing" in {
    test(mkSDRAM()) { dut =>
      waitForInit(dut)
      dut.clock.step(7)
      dut.io.debug.mode.expect(true)
    }
  }

  it should "move to the idle state after setting the mode" in {
    test(mkSDRAM()) { dut =>
      waitForMode(dut)
      dut.clock.step(2)
      dut.io.debug.idle.expect(true)
    }
  }

  it should "move to the active state" in {
    test(mkSDRAM()) { dut =>
      dut.io.mem.rd.poke(true)
      waitForIdle(dut)
      dut.clock.step()
      dut.io.debug.active.expect(true)
    }
  }

  it should "move to the read state" in {
    test(mkSDRAM()) { dut =>
      dut.io.mem.rd.poke(true)
      waitForActive(dut)
      dut.clock.step(2)
      dut.io.debug.read.expect(true)
    }
  }

  it should "move to the write state" in {
    test(mkSDRAM()) { dut =>
      dut.io.mem.wr.poke(true)
      waitForActive(dut)
      dut.clock.step(2)
      dut.io.debug.write.expect(true)
    }
  }

  it should "move to the refresh state" in {
    test(mkSDRAM()) { dut =>
      waitForIdle(dut)
      dut.clock.step(10)
      dut.io.debug.refresh.expect(true)
    }
  }

  it should "return to the idle state from the read state" in {
    test(mkSDRAM()) { dut =>
      dut.io.mem.rd.poke(true)
      waitForRead(dut)
      dut.io.mem.rd.poke(false)
      dut.clock.step(4)
      dut.io.debug.idle.expect(true)
    }
  }

  it should "return to the idle state from the write state" in {
    test(mkSDRAM()) { dut =>
      dut.io.mem.wr.poke(true)
      waitForWrite(dut)
      dut.io.mem.wr.poke(false)
      dut.clock.step(4)
      dut.io.debug.idle.expect(true)
    }
  }

  it should "return to the idle state from the refresh state" in {
    test(mkSDRAM()) { dut =>
      waitForRefresh(dut)
      dut.clock.step(2)
      dut.io.debug.idle.expect(true)
    }
  }

  behavior of "initialize"

  it should "assert clock enable" in {
    test(mkSDRAM()) { dut =>
      dut.io.sdram.cke.expect(true)
    }
  }

  it should "initialize the SDRAM" in {
    test(mkSDRAM()) { dut =>
      // NOP
      dut.io.sdram.cs_n.expect(false)
      dut.io.sdram.ras_n.expect(true)
      dut.io.sdram.cas_n.expect(true)
      dut.io.sdram.we_n.expect(true)
      dut.clock.step()

      // Deselect
      dut.io.sdram.cs_n.expect(true)
      dut.io.sdram.ras_n.expect(false)
      dut.io.sdram.cas_n.expect(false)
      dut.io.sdram.we_n.expect(false)
      dut.clock.step()

      // Precharge
      dut.io.sdram.cs_n.expect(false)
      dut.io.sdram.ras_n.expect(false)
      dut.io.sdram.cas_n.expect(true)
      dut.io.sdram.we_n.expect(false)
      dut.io.sdram.addr.expect(0x400)
      dut.clock.step()

      // Refresh
      dut.io.sdram.cs_n.expect(false)
      dut.io.sdram.ras_n.expect(false)
      dut.io.sdram.cas_n.expect(false)
      dut.io.sdram.we_n.expect(true)
      dut.clock.step(2)

      // Refresh
      dut.io.sdram.cs_n.expect(false)
      dut.io.sdram.ras_n.expect(false)
      dut.io.sdram.cas_n.expect(false)
      dut.io.sdram.we_n.expect(true)
      dut.clock.step(2)

      // Mode
      dut.io.sdram.cs_n.expect(false)
      dut.io.sdram.ras_n.expect(false)
      dut.io.sdram.cas_n.expect(false)
      dut.io.sdram.we_n.expect(false)
      dut.io.sdram.addr.expect(0x020)
    }
  }

  behavior of "idle"

  it should "perform a NOP" in {
    test(mkSDRAM()) { dut =>
      waitForIdle(dut)
      dut.io.sdram.ras_n.expect(true)
      dut.io.sdram.cas_n.expect(true)
      dut.io.sdram.we_n.expect(true)
    }
  }

  it should "deassert the wait signal" in {
    test(mkSDRAM(sdramConfig.copy(burstLength = 2))) { dut =>
      dut.io.mem.waitReq.expect(true)
      waitForIdle(dut)
      dut.io.mem.waitReq.expect(false)
    }
  }

  behavior of "addressing"

  it should "select a bank/row/col" in {
    test(mkSDRAM(sdramConfig.copy(colWidth = 10))) { dut =>
      waitForIdle(dut)

      dut.io.mem.rd.poke(true)
      dut.io.mem.addr.poke(0x1802003)
      waitForActive(dut)
      dut.io.sdram.addr.expect(0x1004) // row
      waitForRead(dut)
      dut.io.sdram.addr.expect(0x401) // col
      dut.io.sdram.bank.expect(1)

      dut.io.mem.rd.poke(true)
      dut.io.mem.addr.poke(0x3ffffff)
      waitForActive(dut)
      dut.io.sdram.addr.expect(0x1fff) // row
      waitForRead(dut)
      dut.io.sdram.addr.expect(0x7ff) // col
      dut.io.sdram.bank.expect(3)
    }
  }

  behavior of "read"

  it should "read from the SDRAM (burst=1)" in {
    test(mkSDRAM()) { dut =>
      waitForIdle(dut)

      // Request
      dut.io.mem.rd.poke(true)
      dut.io.mem.addr.poke(2)
      waitForActive(dut)
      dut.io.sdram.addr.expect(0)
      waitForRead(dut)
      dut.io.sdram.addr.expect(0x401)

      // CAS latency
      dut.clock.step(cycles = 2)

      // Read
      dut.io.sdram.dout.poke(1)
      dut.clock.step()
      dut.io.mem.valid.expect(true)
      dut.io.mem.burstDone.expect(true)
      dut.io.mem.dout.expect(1)
    }
  }

  it should "read from the SDRAM (burst=2)" in {
    test(mkSDRAM(sdramConfig.copy(burstLength = 2))) { dut =>
      waitForIdle(dut)

      // Request
      dut.io.mem.rd.poke(true)
      dut.io.mem.addr.poke(2)
      waitForActive(dut)
      dut.io.sdram.addr.expect(0)
      waitForRead(dut)
      dut.io.sdram.addr.expect(0x401)

      // CAS latency
      dut.clock.step(cycles = 2)

      // Read
      dut.io.sdram.dout.poke(0x1234)
      dut.clock.step()
      dut.io.sdram.dout.poke(0x5678)
      dut.io.mem.valid.expect(true)
      dut.io.mem.dout.expect(0x1234)
      dut.clock.step()
      dut.io.mem.valid.expect(true)
      dut.io.mem.burstDone.expect(true)
      dut.io.mem.dout.expect(0x5678)
    }
  }

  it should "read from the SDRAM (burst=4)" in {
    test(mkSDRAM(sdramConfig.copy(burstLength = 4))) { dut =>
      waitForIdle(dut)

      // Request
      dut.io.mem.rd.poke(true)
      dut.io.mem.addr.poke(2)
      waitForActive(dut)
      dut.io.sdram.addr.expect(0)
      waitForRead(dut)
      dut.io.sdram.addr.expect(0x401)

      // CAS latency
      dut.clock.step(cycles = 2)

      // Read
      dut.io.sdram.dout.poke(0x1234)
      dut.clock.step()
      dut.io.sdram.dout.poke(0x5678)
      dut.io.mem.valid.expect(true)
      dut.io.mem.dout.expect(0x1234)
      dut.clock.step()
      dut.io.sdram.dout.poke(0x1234)
      dut.io.mem.valid.expect(true)
      dut.io.mem.dout.expect(0x5678)
      dut.clock.step()
      dut.io.sdram.dout.poke(0x5678)
      dut.io.mem.valid.expect(true)
      dut.io.mem.dout.expect(0x1234)
      dut.clock.step()
      dut.io.mem.valid.expect(true)
      dut.io.mem.burstDone.expect(true)
      dut.io.mem.dout.expect(0x5678)
    }
  }

  it should "read from the SDRAM (burst=8)" in {
    test(mkSDRAM(sdramConfig.copy(burstLength = 8))) { dut =>
      waitForIdle(dut)

      // Request
      dut.io.mem.rd.poke(true)
      dut.io.mem.addr.poke(2)
      waitForActive(dut)
      dut.io.sdram.addr.expect(0)
      waitForRead(dut)
      dut.io.sdram.addr.expect(0x401)

      // CAS latency
      dut.clock.step(cycles = 2)

      // Read
      dut.io.sdram.dout.poke(0x1234)
      dut.clock.step()
      dut.io.sdram.dout.poke(0x5678)
      dut.io.mem.valid.expect(true)
      dut.io.mem.dout.expect(0x1234)
      dut.clock.step()
      dut.io.sdram.dout.poke(0x1234)
      dut.io.mem.valid.expect(true)
      dut.io.mem.dout.expect(0x5678)
      dut.clock.step()
      dut.io.sdram.dout.poke(0x5678)
      dut.io.mem.valid.expect(true)
      dut.io.mem.dout.expect(0x1234)
      dut.clock.step()
      dut.io.sdram.dout.poke(0x1234)
      dut.io.mem.valid.expect(true)
      dut.io.mem.dout.expect(0x5678)
      dut.clock.step()
      dut.io.sdram.dout.poke(0x5678)
      dut.io.mem.valid.expect(true)
      dut.io.mem.dout.expect(0x1234)
      dut.clock.step()
      dut.io.sdram.dout.poke(0x1234)
      dut.io.mem.valid.expect(true)
      dut.io.mem.dout.expect(0x5678)
      dut.clock.step()
      dut.io.sdram.dout.poke(0x5678)
      dut.io.mem.valid.expect(true)
      dut.io.mem.dout.expect(0x1234)
      dut.clock.step()
      dut.io.mem.valid.expect(true)
      dut.io.mem.burstDone.expect(true)
      dut.io.mem.dout.expect(0x5678)
    }
  }

  it should "assert the wait signal" in {
    test(mkSDRAM(sdramConfig.copy(burstLength = 2))) { dut =>
      waitForIdle(dut)

      // Request
      dut.io.mem.rd.poke(true)
      dut.io.mem.waitReq.expect(false)
      dut.clock.step()
      dut.io.mem.waitReq.expect(true)
      waitForRead(dut)
      dut.io.mem.waitReq.expect(true)

      // CAS latency
      dut.clock.step(cycles = 2)

      // Read
      dut.io.mem.waitReq.expect(true)
      dut.clock.step()
      dut.io.mem.waitReq.expect(false)
    }
  }

  it should "assert the valid signal" in {
    test(mkSDRAM(sdramConfig.copy(burstLength = 2))) { dut =>
      waitForIdle(dut)

      // Request
      dut.io.mem.rd.poke(true)
      waitForRead(dut)

      // CAS latency
      dut.clock.step(cycles = 2)

      // Read
      dut.io.mem.valid.expect(false)
      dut.clock.step()
      dut.io.mem.valid.expect(true)
      dut.clock.step()
      dut.io.mem.valid.expect(true)
      dut.clock.step()
      dut.io.mem.valid.expect(false)
    }
  }

  it should "assert the burst done signal" in {
    test(mkSDRAM(sdramConfig.copy(burstLength = 2))) { dut =>
      waitForIdle(dut)

      // Request
      dut.io.mem.rd.poke(true)
      waitForRead(dut)

      // CAS latency
      dut.clock.step(cycles = 2)

      // Read
      dut.io.mem.burstDone.expect(false)
      dut.clock.step()
      dut.io.mem.burstDone.expect(false)
      dut.clock.step()
      dut.io.mem.burstDone.expect(true)
      dut.clock.step()
      dut.io.mem.burstDone.expect(false)
    }
  }

  behavior of "write"

  it should "write to the SDRAM (burst=1)" in {
    test(mkSDRAM()) { dut =>
      waitForIdle(dut)

      // Request
      dut.io.mem.wr.poke(true)
      dut.io.mem.addr.poke(2)
      waitForActive(dut)
      dut.io.sdram.addr.expect(0)
      dut.clock.step()
      dut.io.mem.din.poke(0x1234)
      dut.clock.step()

      // Write
      dut.io.mem.burstDone.expect(true)
      dut.io.sdram.addr.expect(0x401)
      dut.io.sdram.din.expect(0x1234)
    }
  }

  it should "write to the SDRAM (burst=2)" in {
    test(mkSDRAM(sdramConfig.copy(burstLength = 2))) { dut =>
      waitForIdle(dut)

      // Request
      dut.io.mem.wr.poke(true)
      dut.io.mem.addr.poke(2)
      waitForActive(dut)
      dut.io.sdram.addr.expect(0)
      dut.clock.step()
      dut.io.mem.din.poke(0x1234)
      dut.clock.step()

      // Write
      dut.io.mem.din.poke(0x5678)
      dut.io.sdram.addr.expect(0x401)
      dut.io.sdram.din.expect(0x1234)
      dut.clock.step()
      dut.io.mem.burstDone.expect(true)
      dut.io.sdram.din.expect(0x5678)
    }
  }

  it should "write to the SDRAM (burst=4)" in {
    test(mkSDRAM(sdramConfig.copy(burstLength = 4))) { dut =>
      waitForIdle(dut)

      // Request
      dut.io.mem.wr.poke(true)
      dut.io.mem.addr.poke(2)
      waitForActive(dut)
      dut.io.sdram.addr.expect(0)
      dut.clock.step()
      dut.io.mem.din.poke(0x1234)
      dut.clock.step()

      // Write
      dut.io.mem.din.poke(0x5678)
      dut.io.sdram.addr.expect(0x401)
      dut.io.sdram.din.expect(0x1234)
      dut.clock.step()
      dut.io.mem.din.poke(0x1234)
      dut.io.sdram.din.expect(0x5678)
      dut.clock.step()
      dut.io.mem.din.poke(0x5678)
      dut.io.sdram.din.expect(0x1234)
      dut.clock.step()
      dut.io.mem.burstDone.expect(true)
      dut.io.sdram.din.expect(0x5678)
    }
  }

  it should "write to the SDRAM (burst=8)" in {
    test(mkSDRAM(sdramConfig.copy(burstLength = 8))) { dut =>
      waitForIdle(dut)

      // Request
      dut.io.mem.wr.poke(true)
      dut.io.mem.addr.poke(2)
      waitForActive(dut)
      dut.io.sdram.addr.expect(0)
      dut.clock.step()
      dut.io.mem.din.poke(0x1234)
      dut.clock.step()

      // Write
      dut.io.mem.din.poke(0x5678)
      dut.io.sdram.addr.expect(0x401)
      dut.io.sdram.din.expect(0x1234)
      dut.clock.step()
      dut.io.mem.din.poke(0x1234)
      dut.io.sdram.din.expect(0x5678)
      dut.clock.step()
      dut.io.mem.din.poke(0x5678)
      dut.io.sdram.din.expect(0x1234)
      dut.clock.step()
      dut.io.mem.din.poke(0x1234)
      dut.io.sdram.din.expect(0x5678)
      dut.clock.step()
      dut.io.mem.din.poke(0x5678)
      dut.io.sdram.din.expect(0x1234)
      dut.clock.step()
      dut.io.mem.din.poke(0x1234)
      dut.io.sdram.din.expect(0x5678)
      dut.clock.step()
      dut.io.mem.din.poke(0x5678)
      dut.io.sdram.din.expect(0x1234)
      dut.clock.step()
      dut.io.mem.burstDone.expect(true)
      dut.io.sdram.din.expect(0x5678)
    }
  }

  it should "assert the wait signal" in {
    test(mkSDRAM(sdramConfig.copy(burstLength = 2))) { dut =>
      waitForIdle(dut)

      // Request
      dut.io.mem.wr.poke(true)
      dut.io.mem.waitReq.expect(true)
      waitForActive(dut)

      // Active
      dut.io.mem.waitReq.expect(true)
      dut.clock.step()
      dut.io.mem.waitReq.expect(false)
      dut.clock.step()

      // Write
      dut.io.mem.waitReq.expect(false)
      dut.clock.step()
      dut.io.mem.waitReq.expect(true)
    }
  }

  it should "assert the burst done signal" in {
    test(mkSDRAM(sdramConfig.copy(burstLength = 2))) { dut =>
      waitForIdle(dut)

      // Request
      dut.io.mem.wr.poke(true)
      waitForWrite(dut)

      // Write
      dut.io.mem.burstDone.expect(false)
      dut.clock.step()
      dut.io.mem.burstDone.expect(true)
      dut.clock.step()
      dut.io.mem.burstDone.expect(false)
    }
  }

  behavior of "refresh"

  it should "assert the wait signal when a request is being processed" in {
    test(mkSDRAM()) { dut =>
      waitForRefresh(dut)
      dut.io.mem.rd.poke(true)
      dut.io.mem.waitReq.expect(true)
      dut.clock.step()
      dut.io.mem.waitReq.expect(false)
    }
  }
}
