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
import org.scalatest._
import flatspec.AnyFlatSpec
import matchers.should.Matchers

trait ChannelControllerTestHelpers {
  protected val ymzConfig = YMZ280BConfig(clockFreq = 44100 * 50, numChannels = 1)

  protected def mkChannelController(config: YMZ280BConfig = ymzConfig) = new ChannelController(config)

  protected def startChannel(dut: ChannelController,
                             index: Int,
                             loop: Boolean = false,
                             pitch: Int = 255,
                             level: Int = 255,
                             pan: Int = 7,
                             startAddress: Int = 0,
                             loopStartAddr: Int = 0,
                             loopEndAddr: Int = 0,
                             endAddress: Int = 0) = {
    dut.io.regs(index).flags.keyOn.poke(true)
    dut.io.regs(index).flags.loop.poke(loop)
    dut.io.regs(index).pitch.poke(pitch)
    dut.io.regs(index).level.poke(level)
    dut.io.regs(index).pan.poke(pan)
    dut.io.regs(index).startAddr.poke(startAddress)
    dut.io.regs(index).loopStartAddr.poke(loopStartAddr)
    dut.io.regs(index).loopEndAddr.poke(loopEndAddr)
    dut.io.regs(index).endAddr.poke(endAddress)
  }

  protected def stopChannel(dut: ChannelController, index: Int) = {
    dut.io.regs(index).flags.keyOn.poke(false)
  }

  protected def waitForIdle(dut: ChannelController) =
    while (!dut.io.debug.idle.peekBoolean()) { dut.clock.step() }

  protected def waitForRead(dut: ChannelController) =
    while (!dut.io.debug.read.peekBoolean()) { dut.clock.step() }

  protected def waitForLatch(dut: ChannelController) =
    while (!dut.io.debug.latch.peekBoolean()) { dut.clock.step() }

  protected def waitForCheck(dut: ChannelController) =
    while (!dut.io.debug.check.peekBoolean()) { dut.clock.step() }

  protected def waitForReady(dut: ChannelController) =
    while (!dut.io.debug.ready.peekBoolean()) { dut.clock.step() }

  protected def waitForProcess(dut: ChannelController) =
    while (!dut.io.debug.process.peekBoolean()) { dut.clock.step() }

  protected def waitForWrite(dut: ChannelController) =
    while (!dut.io.debug.write.peekBoolean()) { dut.clock.step() }

  protected def waitForNext(dut: ChannelController) =
    while (!dut.io.debug.next.peekBoolean()) { dut.clock.step() }

  protected def waitForDone(dut: ChannelController) =
    while (!dut.io.debug.done.peekBoolean()) { dut.clock.step() }

  protected def waitForMemRead(dut: ChannelController) =
    while (!dut.io.mem.rd.peekBoolean()) { dut.clock.step() }

  protected def waitForAudioValid(dut: ChannelController) =
    while (!dut.io.audio.valid.peekBoolean()) { dut.clock.step() }
}

class ChannelControllerTest extends AnyFlatSpec with ChiselScalatestTester with Matchers with ChannelControllerTestHelpers {
  behavior of "FSM"

  it should "remain in the init state when the channel controller is disabled" in {
    test(mkChannelController()) { dut =>
      waitForIdle(dut)
      dut.clock.step()
      dut.io.enable.poke(true)
      dut.io.debug.idle.expect(true)
      dut.clock.step()
      dut.io.debug.idle.expect(false)
    }
  }

  it should "move to the read state after initializing" in {
    test(mkChannelController()) { dut =>
      dut.io.enable.poke(true)
      waitForIdle(dut)
      dut.clock.step()
      dut.io.debug.read.expect(true)
    }
  }

  it should "move to the latch state after reading the channel state" in {
    test(mkChannelController()) { dut =>
      dut.io.enable.poke(true)
      waitForRead(dut)
      dut.clock.step()
      dut.io.debug.latch.expect(true)
    }
  }

  it should "move to the check state after latching the channel state" in {
    test(mkChannelController()) { dut =>
      dut.io.enable.poke(true)
      waitForLatch(dut)
      dut.clock.step()
      dut.io.debug.check.expect(true)
    }
  }

  it should "move to the ready state after checking an enabled channel" in {
    test(mkChannelController()) { dut =>
      dut.io.enable.poke(true)
      dut.io.regs(0).flags.keyOn.poke(true)
      waitForCheck(dut)
      dut.clock.step()
      dut.io.debug.ready.expect(true)
    }
  }

  it should "move to the write state after checking a disabled channel" in {
    test(mkChannelController()) { dut =>
      dut.io.enable.poke(true)
      waitForCheck(dut)
      dut.clock.step()
      dut.io.debug.write.expect(true)
    }
  }

  it should "move to the done state after processing all channels" in {
    test(mkChannelController()) { dut =>
      dut.io.enable.poke(true)
      waitForNext(dut)
      dut.clock.step()
      waitForNext(dut)
      dut.clock.step()
      dut.io.debug.done.expect(true)
    }
  }

  it should "move to the idle state after setting the audio output" in {
    test(mkChannelController()) { dut =>
      dut.io.enable.poke(true)
      waitForAudioValid(dut)
      dut.clock.step()
      dut.io.debug.idle.expect(true)
    }
  }

  behavior of "PCM data"

  it should "assert the memory read enable signal" in {
    test(mkChannelController()) { dut =>
      // Start
      dut.io.enable.poke(true)
      startChannel(dut, index = 0)

      // Fetch
      waitForProcess(dut)
      dut.clock.step()
      dut.io.mem.waitReq.poke(true)
      dut.io.mem.rd.expect(true)
      dut.clock.step()
      dut.io.mem.waitReq.poke(false)
      dut.io.mem.rd.expect(true)
      dut.clock.step()
      dut.io.mem.rd.expect(false)
    }
  }

  it should "fetch PCM data for the channels" in {
    test(mkChannelController()) { dut =>
      // Start
      dut.io.enable.poke(true)
      dut.io.mem.valid.poke(true)
      startChannel(dut, index = 0, pitch = 127, startAddress = 1)

      for (n <- Seq(1, 1, 2, 2)) {
        // Fetch
        waitForProcess(dut)
        dut.clock.step()
        dut.io.mem.rd.expect(true)
        dut.io.mem.addr.expect(n.U)
        waitForNext(dut)

        // No fetch
        waitForProcess(dut)
        dut.io.mem.rd.expect(false)
        waitForNext(dut)
      }
    }
  }

  it should "reset the address nibble when restarting a channel" in {
    test(mkChannelController()) { dut =>
      // Start
      dut.io.enable.poke(true)
      dut.io.mem.valid.poke(true)
      startChannel(dut, index = 0, pitch = 127)

      // Process
      waitForProcess(dut)
      dut.io.debug.channelReg.nibble.expect(false)
      waitForNext(dut)
      dut.io.debug.channelReg.nibble.expect(true)

      // Stop
      waitForIdle(dut)
      stopChannel(dut, index = 0)
      dut.clock.step()

      // Start
      waitForIdle(dut)
      startChannel(dut, index = 0, pitch = 127)

      // Process
      waitForProcess(dut)
      dut.io.debug.channelReg.nibble.expect(false)
      waitForNext(dut)
      dut.io.debug.channelReg.nibble.expect(true)
    }
  }

  behavior of "playing"

  it should "play a looped sample" in {
    test(mkChannelController()) { dut =>
      // Start
      dut.io.enable.poke(true)
      startChannel(dut, index = 0, loop = true, loopEndAddr = 1, endAddress = 1)

      // Fetch
      waitForMemRead(dut)
      dut.io.mem.valid.poke(true)
      dut.io.mem.dout.poke(0x12)
      waitForNext(dut)
      dut.io.mem.valid.poke(false)

      // Valid
      waitForAudioValid(dut)
      dut.io.audio.bits.left.expect(0.S)
      dut.io.audio.bits.right.expect(0.S)
      waitForIdle(dut)

      // Fetch
      waitForMemRead(dut)
      dut.io.mem.valid.poke(true)
      dut.io.mem.dout.poke(0x12)
      waitForNext(dut)
      dut.io.mem.valid.poke(false)

      // Valid
      waitForAudioValid(dut)
      dut.io.audio.bits.left.expect(23.S)
      dut.io.audio.bits.right.expect(23.S)
      waitForIdle(dut)

      // Fetch
      waitForMemRead(dut)
      dut.io.mem.valid.poke(true)
      dut.io.mem.dout.poke(0x12)
      waitForNext(dut)
      dut.io.mem.valid.poke(false)

      // Valid
      waitForAudioValid(dut)
      dut.io.audio.bits.left.expect(63.S)
      dut.io.audio.bits.right.expect(63.S)
      waitForIdle(dut)

      // Fetch
      waitForMemRead(dut)
      dut.io.mem.valid.poke(true)
      dut.io.mem.dout.poke(0x12)
      waitForNext(dut)
      dut.io.mem.valid.poke(false)

      // Valid
      waitForAudioValid(dut)
      dut.io.audio.bits.left.expect(86.S)
      dut.io.audio.bits.right.expect(86.S)
      waitForIdle(dut)

      // Fetch
      waitForMemRead(dut)
      dut.io.mem.valid.poke(true)
      dut.io.mem.dout.poke(0x12)
      waitForNext(dut)
      dut.io.mem.valid.poke(false)

      // Valid
      waitForAudioValid(dut)
      dut.io.audio.bits.left.expect(126.S)
      dut.io.audio.bits.right.expect(126.S)
      waitForIdle(dut)

      // Fetch
      waitForMemRead(dut)
      dut.io.mem.valid.poke(true)
      dut.io.mem.dout.poke(0x12)
      waitForNext(dut)
      dut.io.mem.valid.poke(false)

      // Valid
      waitForAudioValid(dut)
      dut.io.audio.bits.left.expect(23.S)
      dut.io.audio.bits.right.expect(23.S)
    }
  }

  it should "sum the channel outputs" in {
    test(mkChannelController(ymzConfig.copy(numChannels = 2))) { dut =>
      // Start
      dut.io.enable.poke(true)
      startChannel(dut, index = 0, pitch = 127)
      startChannel(dut, index = 1, pitch = 127)

      // Fetch (channel 0)
      waitForMemRead(dut)
      dut.io.mem.valid.poke(true)
      dut.io.mem.dout.poke(0x10)
      dut.io.index.expect(0.U)
      waitForNext(dut)
      dut.io.mem.valid.poke(false)

      // Fetch (channel 1)
      waitForMemRead(dut)
      dut.io.mem.valid.poke(true)
      dut.io.mem.dout.poke(0x20)
      dut.io.index.expect(1.U)
      waitForNext(dut)
      dut.io.mem.valid.poke(false)

      // Valid
      waitForAudioValid(dut)
      dut.io.audio.bits.left.expect(0.S)
      dut.io.audio.bits.right.expect(0.S)
      waitForIdle(dut)

      // Valid
      waitForAudioValid(dut)
      dut.io.audio.bits.left.expect(30.S)
      dut.io.audio.bits.right.expect(30.S)
    }
  }

  it should "not allow a channel to play another sample until the key is released" in {
    test(mkChannelController()) { dut =>
      // Start
      dut.io.enable.poke(true)
      dut.io.mem.valid.poke(true)
      startChannel(dut, index = 0, endAddress = 1)

      // Done
      for (_ <- 0 to 4) {
        waitForRead(dut)
        waitForCheck(dut)
      }
      dut.io.active.expect(false)
      dut.io.done.expect(true)

      // Key still down
      waitForRead(dut)
      waitForCheck(dut)
      dut.io.active.expect(false)

      // Stop
      stopChannel(dut, index = 0)
      waitForRead(dut)
      waitForCheck(dut)

      // Start
      startChannel(dut, index = 0, endAddress = 1)
      waitForRead(dut)
      waitForCheck(dut)
      dut.io.active.expect(true)
    }
  }

  behavior of "active"

  it should "assert the active signal when a channel is playing" in {
    test(mkChannelController()) { dut =>
      dut.io.enable.poke(true)

      // Active
      startChannel(dut, index = 0, endAddress = 1)
      waitForCheck(dut)
      dut.io.active.expect(true)

      // Inactive
      stopChannel(dut, index = 0)
      waitForCheck(dut)
      dut.io.active.expect(false)
    }
  }

  it should "deassert the active signal when a channel has reached the end address" in {
    test(mkChannelController()) { dut =>
      // Start
      dut.io.enable.poke(true)
      dut.io.mem.valid.poke(true)
      startChannel(dut, index = 0, endAddress = 1)

      // Status
      for (_ <- 0 to 3) {
        waitForRead(dut)
        waitForCheck(dut)
        dut.io.active.expect(true)
      }

      // Done
      waitForRead(dut)
      waitForCheck(dut)
      dut.io.active.expect(false)
    }
  }

  behavior of "done"

  it should "assert the done signal when a channel has reached the end address" in {
    test(mkChannelController()) { dut =>
      // Start
      dut.io.enable.poke(true)
      dut.io.mem.valid.poke(true)
      startChannel(dut, index = 0, endAddress = 1)

      // Status
      for (_ <- 0 to 3) {
        waitForRead(dut)
        waitForCheck(dut)
        dut.io.done.expect(false)
      }

      // Done
      waitForRead(dut)
      waitForCheck(dut)
      dut.io.done.expect(true)
    }
  }

  it should "not assert the done signal when the loop end address is equal to the end address" in {
    test(mkChannelController()) { dut =>
      // Start
      dut.io.enable.poke(true)
      dut.io.mem.valid.poke(true)
      startChannel(dut, index = 0, loop = true, loopEndAddr = 1, endAddress = 1)

      // Status
      for (n <- Seq(0, 1, 1, 0)) {
        waitForRead(dut)
        waitForCheck(dut)
        dut.io.done.expect(false)
      }
    }
  }

  it should "not assert the done signal when a channel is stopped" in {
    test(mkChannelController()) { dut =>
      // Start
      dut.io.enable.poke(true)
      dut.io.mem.valid.poke(true)
      startChannel(dut, index = 0, endAddress = 1)

      // Stop
      waitForNext(dut)
      stopChannel(dut, index = 0)

      // Status
      waitForCheck(dut)
      dut.io.done.expect(false)
    }
  }

  it should "not assert the done signal when a channel is inactive" in {
    test(mkChannelController()) { dut =>
      // Start
      dut.io.enable.poke(true)
      dut.io.mem.valid.poke(true)
      startChannel(dut, index = 0, endAddress = 1)

      // Done
      for (_ <- 0 to 4) {
        waitForRead(dut)
        waitForCheck(dut)
      }
      dut.io.done.expect(true)

      // Disabled
      waitForRead(dut)
      waitForCheck(dut)
      dut.io.done.expect(false)
    }
  }
}
