/*
 *    __   __     __  __     __         __
 *   /\ "-.\ \   /\ \/\ \   /\ \       /\ \
 *   \ \ \-.  \  \ \ \_\ \  \ \ \____  \ \ \____
 *    \ \_\\"\_\  \ \_____\  \ \_____\  \ \_____\
 *     \/_/ \/_/   \/_____/   \/_____/   \/_____/
 *    ______     ______       __     ______     ______     ______
 *   /\  __ \   /\  == \     /\ \   /\  ___\   /\  ___\   /\__  _\
 *   \ \ \/\ \  \ \  __<    _\_\ \  \ \  __\   \ \ \____  \/_/\ \/
 *    \ \_____\  \ \_____\ /\_____\  \ \_____\  \ \_____\    \ \_\
 *     \/_____/   \/_____/ \/_____/   \/_____/   \/_____/     \/_/
 *
 *  https://joshbassett.info
 *  https://twitter.com/nullobject
 *  https://github.com/nullobject
 *
 *  Copyright (c) 2020 Josh Bassett
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
 */

package axon.mem

import chisel3._
import chiseltest._
import org.scalatest._

trait SDRAMTestHelpers {
  protected val sdramConfig = SDRAMConfig(
    clockFreq = 100000000,
    tINIT = 20,
    tMRD = 10,
    tRC = 20,
    tRCD = 20,
    tRP = 10,
    tWR = 10,
    tREFI = 100
  )

  protected def mkSDRAM(config: SDRAMConfig = sdramConfig) = new SDRAM(config)

  protected def waitForInit(dut: SDRAM) =
    while (!dut.io.debug.init.peek().litToBoolean) { dut.clock.step() }

  protected def waitForMode(dut: SDRAM) =
    while (!dut.io.debug.mode.peek().litToBoolean) { dut.clock.step() }

  protected def waitForIdle(dut: SDRAM) =
    while (!dut.io.debug.idle.peek().litToBoolean) { dut.clock.step() }

  protected def waitForActive(dut: SDRAM) =
    while (!dut.io.debug.active.peek().litToBoolean) { dut.clock.step() }

  protected def waitForRead(dut: SDRAM) =
    while (!dut.io.debug.read.peek().litToBoolean) { dut.clock.step() }

  protected def waitForWrite(dut: SDRAM) =
    while (!dut.io.debug.write.peek().litToBoolean) { dut.clock.step() }

  protected def waitForRefresh(dut: SDRAM) =
    while (!dut.io.debug.refresh.peek().litToBoolean) { dut.clock.step() }
}

class SDRAMTest extends FlatSpec with ChiselScalatestTester with Matchers with SDRAMTestHelpers {
  behavior of "FSM"

  it should "move to the mode state after initializing" in {
    test(mkSDRAM()) { dut =>
      waitForInit(dut)
      dut.clock.step(7)
      dut.io.debug.mode.expect(true.B)
    }
  }

  it should "move to the idle state after setting the mode" in {
    test(mkSDRAM()) { dut =>
      waitForMode(dut)
      dut.clock.step(2)
      dut.io.debug.idle.expect(true.B)
    }
  }

  it should "move to the active state" in {
    test(mkSDRAM()) { dut =>
      dut.io.mem.rd.poke(true.B)
      waitForIdle(dut)
      dut.clock.step()
      dut.io.debug.active.expect(true.B)
    }
  }

  it should "move to the read state" in {
    test(mkSDRAM()) { dut =>
      dut.io.mem.rd.poke(true.B)
      waitForActive(dut)
      dut.clock.step(2)
      dut.io.debug.read.expect(true.B)
    }
  }

  it should "move to the write state" in {
    test(mkSDRAM()) { dut =>
      dut.io.mem.wr.poke(true.B)
      waitForActive(dut)
      dut.clock.step(2)
      dut.io.debug.write.expect(true.B)
    }
  }

  it should "move to the refresh state" in {
    test(mkSDRAM()) { dut =>
      waitForIdle(dut)
      dut.clock.step(10)
      dut.io.debug.refresh.expect(true.B)
    }
  }

  it should "return to the idle state from the read state" in {
    test(mkSDRAM()) { dut =>
      dut.io.mem.rd.poke(true.B)
      waitForRead(dut)
      dut.io.mem.rd.poke(false.B)
      dut.clock.step(4)
      dut.io.debug.idle.expect(true.B)
    }
  }

  it should "return to the idle state from the write state" in {
    test(mkSDRAM()) { dut =>
      dut.io.mem.wr.poke(true.B)
      waitForWrite(dut)
      dut.io.mem.wr.poke(false.B)
      dut.clock.step(4)
      dut.io.debug.idle.expect(true.B)
    }
  }

  it should "return to the idle state from the refresh state" in {
    test(mkSDRAM()) { dut =>
      waitForRefresh(dut)
      dut.clock.step(2)
      dut.io.debug.idle.expect(true.B)
    }
  }

  behavior of "initialize"

  it should "initialize the SDRAM" in {
    test(mkSDRAM()) { dut =>
      // NOP
      dut.io.sdram.cs_n.expect(false.B)
      dut.io.sdram.ras_n.expect(true.B)
      dut.io.sdram.cas_n.expect(true.B)
      dut.io.sdram.we_n.expect(true.B)
      dut.clock.step()

      // Deselect
      dut.io.sdram.cs_n.expect(true.B)
      dut.io.sdram.ras_n.expect(false.B)
      dut.io.sdram.cas_n.expect(false.B)
      dut.io.sdram.we_n.expect(false.B)
      dut.clock.step()

      // Precharge
      dut.io.sdram.cs_n.expect(false.B)
      dut.io.sdram.ras_n.expect(false.B)
      dut.io.sdram.cas_n.expect(true.B)
      dut.io.sdram.we_n.expect(false.B)
      dut.io.sdram.addr.expect(0x400.U)
      dut.clock.step()

      // Refresh
      dut.io.sdram.cs_n.expect(false.B)
      dut.io.sdram.ras_n.expect(false.B)
      dut.io.sdram.cas_n.expect(false.B)
      dut.io.sdram.we_n.expect(true.B)
      dut.clock.step(2)

      // Refresh
      dut.io.sdram.cs_n.expect(false.B)
      dut.io.sdram.ras_n.expect(false.B)
      dut.io.sdram.cas_n.expect(false.B)
      dut.io.sdram.we_n.expect(true.B)
      dut.clock.step(2)

      // Mode
      dut.io.sdram.cs_n.expect(false.B)
      dut.io.sdram.ras_n.expect(false.B)
      dut.io.sdram.cas_n.expect(false.B)
      dut.io.sdram.we_n.expect(false.B)
      dut.io.sdram.addr.expect(0x020.U)
    }
  }

  behavior of "idle"

  it should "perform a NOP" in {
    test(mkSDRAM()) { dut =>
      waitForIdle(dut)
      dut.io.sdram.ras_n.expect(true.B)
      dut.io.sdram.cas_n.expect(true.B)
      dut.io.sdram.we_n.expect(true.B)
    }
  }

  it should "deassert the wait signal" in {
    test(mkSDRAM(sdramConfig.copy(burstLength = 2))) { dut =>
      waitForIdle(dut)
      dut.io.mem.waitReq.expect(false.B)
    }
  }

  behavior of "addressing"

  it should "select a bank" in {
    test(mkSDRAM()) { dut =>
      waitForIdle(dut)

      dut.io.mem.rd.poke(true.B)
      dut.io.mem.addr.poke(0x1802003.U)
      waitForActive(dut)
      dut.io.sdram.addr.expect(8.U) // row
      waitForRead(dut)
      dut.io.sdram.addr.expect(0x401.U) // col
      dut.io.sdram.bank.expect(3.U)
    }
  }

  behavior of "read"

  it should "read from the SDRAM (burst=1)" in {
    test(mkSDRAM()) { dut =>
      waitForIdle(dut)

      // Request
      dut.io.mem.rd.poke(true.B)
      dut.io.mem.addr.poke(2.U)
      waitForActive(dut)
      dut.io.sdram.addr.expect(0.U)
      waitForRead(dut)
      dut.io.sdram.addr.expect(0x401.U)

      // CAS latency
      dut.clock.step(cycles = 2)

      // Read
      dut.io.sdram.dout.poke(1.U)
      dut.clock.step()
      dut.io.mem.valid.expect(true.B)
      dut.io.mem.burstDone.expect(true.B)
      dut.io.mem.dout.expect(1.U)
    }
  }

  it should "read from the SDRAM (burst=2)" in {
    test(mkSDRAM(sdramConfig.copy(burstLength = 2))) { dut =>
      waitForIdle(dut)

      // Request
      dut.io.mem.rd.poke(true.B)
      dut.io.mem.addr.poke(2.U)
      waitForActive(dut)
      dut.io.sdram.addr.expect(0.U)
      waitForRead(dut)
      dut.io.sdram.addr.expect(0x401.U)

      // CAS latency
      dut.clock.step(cycles = 2)

      // Read
      dut.io.sdram.dout.poke(0x1234.U)
      dut.clock.step()
      dut.io.sdram.dout.poke(0x5678.U)
      dut.io.mem.valid.expect(true.B)
      dut.io.mem.dout.expect(0x1234.U)
      dut.clock.step()
      dut.io.mem.valid.expect(true.B)
      dut.io.mem.burstDone.expect(true.B)
      dut.io.mem.dout.expect(0x5678.U)
    }
  }

  it should "read from the SDRAM (burst=4)" in {
    test(mkSDRAM(sdramConfig.copy(burstLength = 4))) { dut =>
      waitForIdle(dut)

      // Request
      dut.io.mem.rd.poke(true.B)
      dut.io.mem.addr.poke(2.U)
      waitForActive(dut)
      dut.io.sdram.addr.expect(0.U)
      waitForRead(dut)
      dut.io.sdram.addr.expect(0x401.U)

      // CAS latency
      dut.clock.step(cycles = 2)

      // Read
      dut.io.sdram.dout.poke(0x1234.U)
      dut.clock.step()
      dut.io.sdram.dout.poke(0x5678.U)
      dut.io.mem.valid.expect(true.B)
      dut.io.mem.dout.expect(0x1234.U)
      dut.clock.step()
      dut.io.sdram.dout.poke(0x1234.U)
      dut.io.mem.valid.expect(true.B)
      dut.io.mem.dout.expect(0x5678.U)
      dut.clock.step()
      dut.io.sdram.dout.poke(0x5678.U)
      dut.io.mem.valid.expect(true.B)
      dut.io.mem.dout.expect(0x1234.U)
      dut.clock.step()
      dut.io.mem.valid.expect(true.B)
      dut.io.mem.burstDone.expect(true.B)
      dut.io.mem.dout.expect(0x5678.U)
    }
  }

  it should "read from the SDRAM (burst=8)" in {
    test(mkSDRAM(sdramConfig.copy(burstLength = 8))) { dut =>
      waitForIdle(dut)

      // Request
      dut.io.mem.rd.poke(true.B)
      dut.io.mem.addr.poke(2.U)
      waitForActive(dut)
      dut.io.sdram.addr.expect(0.U)
      waitForRead(dut)
      dut.io.sdram.addr.expect(0x401.U)

      // CAS latency
      dut.clock.step(cycles = 2)

      // Read
      dut.io.sdram.dout.poke(0x1234.U)
      dut.clock.step()
      dut.io.sdram.dout.poke(0x5678.U)
      dut.io.mem.valid.expect(true.B)
      dut.io.mem.dout.expect(0x1234.U)
      dut.clock.step()
      dut.io.sdram.dout.poke(0x1234.U)
      dut.io.mem.valid.expect(true.B)
      dut.io.mem.dout.expect(0x5678.U)
      dut.clock.step()
      dut.io.sdram.dout.poke(0x5678.U)
      dut.io.mem.valid.expect(true.B)
      dut.io.mem.dout.expect(0x1234.U)
      dut.clock.step()
      dut.io.sdram.dout.poke(0x1234.U)
      dut.io.mem.valid.expect(true.B)
      dut.io.mem.dout.expect(0x5678.U)
      dut.clock.step()
      dut.io.sdram.dout.poke(0x5678.U)
      dut.io.mem.valid.expect(true.B)
      dut.io.mem.dout.expect(0x1234.U)
      dut.clock.step()
      dut.io.sdram.dout.poke(0x1234.U)
      dut.io.mem.valid.expect(true.B)
      dut.io.mem.dout.expect(0x5678.U)
      dut.clock.step()
      dut.io.sdram.dout.poke(0x5678.U)
      dut.io.mem.valid.expect(true.B)
      dut.io.mem.dout.expect(0x1234.U)
      dut.clock.step()
      dut.io.mem.valid.expect(true.B)
      dut.io.mem.burstDone.expect(true.B)
      dut.io.mem.dout.expect(0x5678.U)
    }
  }

  it should "assert the wait signal" in {
    test(mkSDRAM(sdramConfig.copy(burstLength = 2))) { dut =>
      waitForIdle(dut)

      // Request
      dut.io.mem.rd.poke(true.B)
      dut.io.mem.waitReq.expect(false.B)
      dut.clock.step()
      dut.io.mem.waitReq.expect(true.B)
      waitForRead(dut)
      dut.io.mem.waitReq.expect(true.B)

      // CAS latency
      dut.clock.step(cycles = 2)

      // Read
      dut.io.mem.waitReq.expect(true.B)
      dut.clock.step()
      dut.io.mem.waitReq.expect(false.B)
    }
  }

  it should "assert the valid signal" in {
    test(mkSDRAM(sdramConfig.copy(burstLength = 2))) { dut =>
      waitForIdle(dut)

      // Request
      dut.io.mem.rd.poke(true.B)
      waitForRead(dut)

      // CAS latency
      dut.clock.step(cycles = 2)

      // Read
      dut.io.mem.valid.expect(false.B)
      dut.clock.step()
      dut.io.mem.valid.expect(true.B)
      dut.clock.step()
      dut.io.mem.valid.expect(true.B)
      dut.clock.step()
      dut.io.mem.valid.expect(false.B)
    }
  }

  it should "assert the burst done signal" in {
    test(mkSDRAM(sdramConfig.copy(burstLength = 2))) { dut =>
      waitForIdle(dut)

      // Request
      dut.io.mem.rd.poke(true.B)
      waitForRead(dut)

      // CAS latency
      dut.clock.step(cycles = 2)

      // Read
      dut.io.mem.burstDone.expect(false.B)
      dut.clock.step()
      dut.io.mem.burstDone.expect(false.B)
      dut.clock.step()
      dut.io.mem.burstDone.expect(true.B)
      dut.clock.step()
      dut.io.mem.burstDone.expect(false.B)
    }
  }

  behavior of "write"

  it should "write to the SDRAM (burst=1)" in {
    test(mkSDRAM()) { dut =>
      waitForIdle(dut)

      // Request
      dut.io.mem.wr.poke(true.B)
      dut.io.mem.addr.poke(2.U)
      waitForActive(dut)
      dut.io.sdram.addr.expect(0.U)
      dut.clock.step()
      dut.io.mem.din.poke(0x1234.U)
      dut.clock.step()

      // Write
      dut.io.mem.burstDone.expect(true.B)
      dut.io.sdram.addr.expect(0x401.U)
      dut.io.sdram.din.expect(0x1234.U)
    }
  }

  it should "write to the SDRAM (burst=2)" in {
    test(mkSDRAM(sdramConfig.copy(burstLength = 2))) { dut =>
      waitForIdle(dut)

      // Request
      dut.io.mem.wr.poke(true.B)
      dut.io.mem.addr.poke(2.U)
      waitForActive(dut)
      dut.io.sdram.addr.expect(0.U)
      dut.clock.step()
      dut.io.mem.din.poke(0x1234.U)
      dut.clock.step()

      // Write
      dut.io.mem.din.poke(0x5678.U)
      dut.io.sdram.addr.expect(0x401.U)
      dut.io.sdram.din.expect(0x1234.U)
      dut.clock.step()
      dut.io.mem.burstDone.expect(true.B)
      dut.io.sdram.din.expect(0x5678.U)
    }
  }

  it should "write to the SDRAM (burst=4)" in {
    test(mkSDRAM(sdramConfig.copy(burstLength = 4))) { dut =>
      waitForIdle(dut)

      // Request
      dut.io.mem.wr.poke(true.B)
      dut.io.mem.addr.poke(2.U)
      waitForActive(dut)
      dut.io.sdram.addr.expect(0.U)
      dut.clock.step()
      dut.io.mem.din.poke(0x1234.U)
      dut.clock.step()

      // Write
      dut.io.mem.din.poke(0x5678.U)
      dut.io.sdram.addr.expect(0x401.U)
      dut.io.sdram.din.expect(0x1234.U)
      dut.clock.step()
      dut.io.mem.din.poke(0x1234.U)
      dut.io.sdram.din.expect(0x5678.U)
      dut.clock.step()
      dut.io.mem.din.poke(0x5678.U)
      dut.io.sdram.din.expect(0x1234.U)
      dut.clock.step()
      dut.io.mem.burstDone.expect(true.B)
      dut.io.sdram.din.expect(0x5678.U)
    }
  }

  it should "write to the SDRAM (burst=8)" in {
    test(mkSDRAM(sdramConfig.copy(burstLength = 8))) { dut =>
      waitForIdle(dut)

      // Request
      dut.io.mem.wr.poke(true.B)
      dut.io.mem.addr.poke(2.U)
      waitForActive(dut)
      dut.io.sdram.addr.expect(0.U)
      dut.clock.step()
      dut.io.mem.din.poke(0x1234.U)
      dut.clock.step()

      // Write
      dut.io.mem.din.poke(0x5678.U)
      dut.io.sdram.addr.expect(0x401.U)
      dut.io.sdram.din.expect(0x1234.U)
      dut.clock.step()
      dut.io.mem.din.poke(0x1234.U)
      dut.io.sdram.din.expect(0x5678.U)
      dut.clock.step()
      dut.io.mem.din.poke(0x5678.U)
      dut.io.sdram.din.expect(0x1234.U)
      dut.clock.step()
      dut.io.mem.din.poke(0x1234.U)
      dut.io.sdram.din.expect(0x5678.U)
      dut.clock.step()
      dut.io.mem.din.poke(0x5678.U)
      dut.io.sdram.din.expect(0x1234.U)
      dut.clock.step()
      dut.io.mem.din.poke(0x1234.U)
      dut.io.sdram.din.expect(0x5678.U)
      dut.clock.step()
      dut.io.mem.din.poke(0x5678.U)
      dut.io.sdram.din.expect(0x1234.U)
      dut.clock.step()
      dut.io.mem.burstDone.expect(true.B)
      dut.io.sdram.din.expect(0x5678.U)
    }
  }

  it should "assert the wait signal" in {
    test(mkSDRAM(sdramConfig.copy(burstLength = 2))) { dut =>
      waitForIdle(dut)

      // Request
      dut.io.mem.wr.poke(true.B)
      dut.io.mem.waitReq.expect(true.B)
      waitForActive(dut)

      // Active
      dut.io.mem.waitReq.expect(true.B)
      dut.clock.step()
      dut.io.mem.waitReq.expect(false.B)
      dut.clock.step()

      // Write
      dut.io.mem.waitReq.expect(false.B)
      dut.clock.step()
      dut.io.mem.waitReq.expect(true.B)
    }
  }

  it should "assert the burst done signal" in {
    test(mkSDRAM(sdramConfig.copy(burstLength = 2))) { dut =>
      waitForIdle(dut)

      // Request
      dut.io.mem.wr.poke(true.B)
      waitForWrite(dut)

      // Write
      dut.io.mem.burstDone.expect(false.B)
      dut.clock.step()
      dut.io.mem.burstDone.expect(true.B)
      dut.clock.step()
      dut.io.mem.burstDone.expect(false.B)
    }
  }

  behavior of "refresh"

  it should "assert the wait signal when a request is being processed" in {
    test(mkSDRAM()) { dut =>
      waitForRefresh(dut)
      dut.io.mem.rd.poke(true.B)
      dut.io.mem.waitReq.expect(true.B)
      dut.clock.step()
      dut.io.mem.waitReq.expect(false.B)
    }
  }
}
