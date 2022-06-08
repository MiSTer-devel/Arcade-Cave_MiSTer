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

package axon.snd

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

trait YMZ280BTestHelpers {
  protected val config = YMZ280BConfig(clockFreq = 44100, numChannels = 2)

  protected def mkADPCM = new YMZ280B(config)

  protected def readStatusReg(dut: YMZ280B) = {
    dut.io.cpu.rd.poke(true)
    dut.io.cpu.addr.poke(1)
    dut.clock.step()
    dut.io.cpu.rd.poke(false)
    dut.io.cpu.dout.peekInt()
  }

  protected def writeReg(dut: YMZ280B, addr: Int, data: Int) = {
    dut.io.cpu.wr.poke(true)
    dut.io.cpu.addr.poke(0)
    dut.io.cpu.din.poke(addr)
    dut.clock.step()
    dut.io.cpu.addr.poke(1)
    dut.io.cpu.din.poke(data)
    dut.clock.step()
    dut.io.cpu.wr.poke(false)
  }
}

class YMZ280BTest extends AnyFlatSpec with ChiselScalatestTester with Matchers with YMZ280BTestHelpers {
  behavior of "function register"

  it should "allow writing the channel pitch" in {
    test(mkADPCM) { dut =>
      writeReg(dut, 0x00, 1)
      dut.io.debug.channels(0).pitch.expect(1.U)
    }
  }

  it should "allow writing the channel level" in {
    test(mkADPCM) { dut =>
      writeReg(dut, 0x02, 3)
      dut.io.debug.channels(0).level.expect(3.U)
    }
  }

  it should "allow writing the start address" in {
    test(mkADPCM) { dut =>
      writeReg(dut, 0x60, 0x03)
      writeReg(dut, 0x40, 0x02)
      writeReg(dut, 0x20, 0x01)
      dut.io.debug.channels(0).startAddr.expect("h010203".U)
    }
  }

  it should "allow writing the loop start address" in {
    test(mkADPCM) { dut =>
      writeReg(dut, 0x61, 0x03)
      writeReg(dut, 0x41, 0x02)
      writeReg(dut, 0x21, 0x01)
      dut.io.debug.channels(0).loopStartAddr.expect("h010203".U)
    }
  }

  it should "allow writing the loop end address" in {
    test(mkADPCM) { dut =>
      writeReg(dut, 0x62, 0x03)
      writeReg(dut, 0x42, 0x02)
      writeReg(dut, 0x22, 0x01)
      dut.io.debug.channels(0).loopEndAddr.expect("h010203".U)
    }
  }

  it should "allow writing the end address" in {
    test(mkADPCM) { dut =>
      writeReg(dut, 0x63, 0x03)
      writeReg(dut, 0x43, 0x02)
      writeReg(dut, 0x23, 0x01)
      dut.io.debug.channels(0).endAddr.expect("h010203".U)
    }
  }

  behavior of "status register"

  it should "allow reading the status of the channels" in {
    test(mkADPCM) { dut =>
      dut.io.rom.valid.poke(true)
      writeReg(dut, 0x00, 0xff) // channel 0 pitch
      writeReg(dut, 0x63, 0x01) // channel 0 end address
      writeReg(dut, 0x01, 0x80) // channel 0 key on
      writeReg(dut, 0xff, 0x80) // key on enable
      dut.clock.step(100)
      readStatusReg(dut) shouldBe 1
      dut.io.debug.statusReg.expect(0.U)
    }
  }

  behavior of "utility register"

  it should "allow writing the IRQ mask" in {
    test(mkADPCM) { dut =>
      writeReg(dut, 0xfe, 0x12)
      dut.io.debug.utilReg.irqMask.expect(0x12.U)
    }
  }

  it should "allow writing the enable flags" in {
    test(mkADPCM) { dut =>
      writeReg(dut, 0xff, 0xd0)
      dut.io.debug.utilReg.flags.keyOnEnable.expect(true)
      dut.io.debug.utilReg.flags.memEnable.expect(true)
      dut.io.debug.utilReg.flags.irqEnable.expect(true)
    }
  }

  behavior of "IRQ"

  it should "assert the IRQ signal for channels that are done" in {
    test(mkADPCM) { dut =>
      dut.io.rom.valid.poke(true)
      writeReg(dut, 0x04, 0xff) // channel 1 pitch
      writeReg(dut, 0x67, 0x01) // channel 1 end address
      writeReg(dut, 0x05, 0x80) // channel 1 key on
      writeReg(dut, 0xfe, 0x02) // IRQ mask
      writeReg(dut, 0xff, 0x90) // key on & IRQ enable
      dut.clock.step(100)
      dut.io.irq.expect(true)
    }
  }

  it should "not assert the IRQ signal for masked channels" in {
    test(mkADPCM) { dut =>
      dut.io.rom.valid.poke(true)
      writeReg(dut, 0x04, 0xff) // channel 1 pitch
      writeReg(dut, 0x67, 0x01) // channel 1 end address
      writeReg(dut, 0x05, 0x80) // channel 1 key on
      writeReg(dut, 0xfe, 0x00) // IRQ mask
      writeReg(dut, 0xff, 0x90) // key on & IRQ enable
      dut.clock.step(100)
      dut.io.irq.expect(false)
    }
  }
}
