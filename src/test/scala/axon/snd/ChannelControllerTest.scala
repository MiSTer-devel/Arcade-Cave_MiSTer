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

package axon.snd

import chisel3._
import chiseltest._
import org.scalatest._

trait ChannelControllerTestHelpers {
  protected val ymzConfig = YMZ280BConfig(clockFreq = 44100*50, numChannels = 1)

  protected def mkChannelController(config: YMZ280BConfig = ymzConfig) = new ChannelController(config)

  protected def startChannel(dut: ChannelController,
                             channelIndex: Int,
                             loop: Boolean = false,
                             pitch: Int = 255,
                             level: Int = 255,
                             pan: Int = 7,
                             startAddress: Int = 0,
                             loopStartAddr: Int = 0,
                             loopEndAddr: Int = 0,
                             endAddress: Int = 0) = {
    dut.io.channelRegs(channelIndex).flags.keyOn.poke(true.B)
    dut.io.channelRegs(channelIndex).flags.loop.poke(loop.B)
    dut.io.channelRegs(channelIndex).pitch.poke(pitch.U)
    dut.io.channelRegs(channelIndex).level.poke(level.U)
    dut.io.channelRegs(channelIndex).pan.poke(pan.U)
    dut.io.channelRegs(channelIndex).startAddr.poke(startAddress.U)
    dut.io.channelRegs(channelIndex).loopStartAddr.poke(loopStartAddr.U)
    dut.io.channelRegs(channelIndex).loopEndAddr.poke(loopEndAddr.U)
    dut.io.channelRegs(channelIndex).endAddr.poke(endAddress.U)
  }

  protected def stopChannel(dut: ChannelController, channelIndex: Int) = {
    dut.io.channelRegs(channelIndex).flags.keyOn.poke(false.B)
  }

  protected def waitForIdle(dut: ChannelController) =
    while(!dut.io.debug.idle.peek().litToBoolean) { dut.clock.step() }

  protected def waitForRead(dut: ChannelController) =
    while(!dut.io.debug.read.peek().litToBoolean) { dut.clock.step() }

  protected def waitForLatch(dut: ChannelController) =
    while(!dut.io.debug.latch.peek().litToBoolean) { dut.clock.step() }

  protected def waitForCheck(dut: ChannelController) =
    while(!dut.io.debug.check.peek().litToBoolean) { dut.clock.step() }

  protected def waitForReady(dut: ChannelController) =
    while(!dut.io.debug.ready.peek().litToBoolean) { dut.clock.step() }

  protected def waitForProcess(dut: ChannelController) =
    while(!dut.io.debug.process.peek().litToBoolean) { dut.clock.step() }

  protected def waitForWrite(dut: ChannelController) =
    while(!dut.io.debug.write.peek().litToBoolean) { dut.clock.step() }

  protected def waitForNext(dut: ChannelController) =
    while(!dut.io.debug.next.peek().litToBoolean) { dut.clock.step() }

  protected def waitForDone(dut: ChannelController) =
    while(!dut.io.debug.done.peek().litToBoolean) { dut.clock.step() }

  protected def waitForMemRead(dut: ChannelController) =
    while(!dut.io.mem.rd.peek().litToBoolean) { dut.clock.step() }

  protected def waitForAudioValid(dut: ChannelController) =
    while(!dut.io.audio.valid.peek().litToBoolean) { dut.clock.step() }
}

class ChannelControllerTest extends FlatSpec with ChiselScalatestTester with Matchers with ChannelControllerTestHelpers {
  behavior of "FSM"

  it should "remain in the init state when the channel controller is disabled" in {
    test(mkChannelController()) { dut =>
      waitForIdle(dut)
      dut.clock.step()
      dut.io.enable.poke(true.B)
      dut.io.debug.idle.expect(true.B)
      dut.clock.step()
      dut.io.debug.idle.expect(false.B)
    }
  }

  it should "move to the read state after initializing" in {
    test(mkChannelController()) { dut =>
      dut.io.enable.poke(true.B)
      waitForIdle(dut)
      dut.clock.step()
      dut.io.debug.read.expect(true.B)
    }
  }

  it should "move to the latch state after reading the channel state" in {
    test(mkChannelController()) { dut =>
      dut.io.enable.poke(true.B)
      waitForRead(dut)
      dut.clock.step()
      dut.io.debug.latch.expect(true.B)
    }
  }

  it should "move to the check state after latch the channel state" in {
    test(mkChannelController()) { dut =>
      dut.io.enable.poke(true.B)
      waitForLatch(dut)
      dut.clock.step()
      dut.io.debug.check.expect(true.B)
    }
  }

  it should "move to the ready state after checking an enabled channel" in {
    test(mkChannelController()) { dut =>
      dut.io.enable.poke(true.B)
      dut.io.channelRegs(0).flags.keyOn.poke(true.B)
      waitForCheck(dut)
      dut.clock.step()
      dut.io.debug.ready.expect(true.B)
    }
  }

  it should "move to the write state after checking a disabled channel" in {
    test(mkChannelController()) { dut =>
      dut.io.enable.poke(true.B)
      waitForCheck(dut)
      dut.clock.step()
      dut.io.debug.write.expect(true.B)
    }
  }

  it should "assert the channel state valid signal during the write state" in {
    test(mkChannelController()) { dut =>
      dut.io.enable.poke(true.B)
      dut.io.channelState.valid.expect(false.B)
      waitForWrite(dut)
      dut.io.channelState.valid.expect(true.B)
      dut.clock.step()
      dut.io.channelState.valid.expect(false.B)
    }
  }

  it should "move to the done state after processing all channels" in {
    test(mkChannelController()) { dut =>
      dut.io.enable.poke(true.B)
      waitForNext(dut)
      dut.clock.step()
      waitForNext(dut)
      dut.clock.step()
      dut.io.debug.done.expect(true.B)
    }
  }

  it should "move to the idle state after setting the audio output" in {
    test(mkChannelController()) { dut =>
      dut.io.enable.poke(true.B)
      waitForAudioValid(dut)
      dut.clock.step()
      dut.io.debug.idle.expect(true.B)
    }
  }

  behavior of "PCM data"

  it should "fetch PCM data for the channels" in {
    test(mkChannelController()) { dut =>
      // Start
      dut.io.enable.poke(true.B)
      startChannel(dut, channelIndex = 0, pitch = 127, startAddress = 1)

      Seq(1, 1, 2, 2).foreach { n =>
        // Fetch
        waitForProcess(dut)
        dut.io.mem.rd.expect(true.B)
        dut.io.mem.addr.expect(n.U)
        waitForNext(dut)

        // No fetch
        waitForProcess(dut)
        dut.io.mem.rd.expect(false.B)
        waitForNext(dut)
      }
    }
  }

  behavior of "playing"

  it should "sum the channel outputs" in {
    test(mkChannelController(ymzConfig.copy(numChannels = 2))) { dut =>
      // Start
      dut.io.enable.poke(true.B)
      startChannel(dut, channelIndex = 0, pitch = 127)
      startChannel(dut, channelIndex = 1, pitch = 127)

      // Fetch (channel 0)
      waitForMemRead(dut)
      dut.clock.step()
      dut.io.mem.dout.poke(16.U)
      waitForNext(dut)

      // Fetch (channel 1)
      waitForMemRead(dut)
      dut.clock.step()
      dut.io.mem.dout.poke(32.U)
      waitForNext(dut)

      // Valid
      waitForAudioValid(dut)
      dut.io.audio.bits.left.expect(0.S)
      dut.io.audio.bits.right.expect(0.S)
      waitForIdle(dut)

      // Valid
      waitForAudioValid(dut)
      dut.io.audio.bits.left.expect(62.S)
      dut.io.audio.bits.right.expect(62.S)
    }
  }

  it should "assert the done signal when a channel has reached the end address" in {
    test(mkChannelController()) { dut =>
      // Start
      dut.io.enable.poke(true.B)
      startChannel(dut, channelIndex = 0, endAddress = 1)

      // Status
      0.to(2).foreach { _ =>
        waitForWrite(dut)
        dut.io.channelIndex.expect(0.U)
        dut.io.channelState.bits.done.expect(false.B)
        waitForNext(dut)
      }

      // Done
      waitForWrite(dut)
      dut.io.channelIndex.expect(0.U)
      dut.io.channelState.bits.done.expect(true.B)
      dut.io.channelState.bits.addr.expect(1.U)
    }
  }

  it should "not assert the done signal when a channel has reached the loop end address" in {
    test(mkChannelController()) { dut =>
      // Start
      dut.io.enable.poke(true.B)
      startChannel(dut, channelIndex = 0, loop = true, loopEndAddr = 1, endAddress = 1)

      // Status
      Seq(0, 1, 1, 0).foreach { n =>
        waitForWrite(dut)
        dut.io.channelIndex.expect(0.U)
        dut.io.channelState.bits.done.expect(false.B)
        dut.io.channelState.bits.addr.expect(n.U)
        waitForNext(dut)
      }
    }
  }

  it should "not assert the done signal when a channel is stopped" in {
    test(mkChannelController()) { dut =>
      // Start
      dut.io.enable.poke(true.B)
      startChannel(dut, channelIndex = 0, endAddress = 1)

      // Stop
      waitForNext(dut)
      stopChannel(dut, channelIndex = 0)

      // Status
      waitForWrite(dut)
      dut.io.channelIndex.expect(0.U)
      dut.io.channelState.bits.done.expect(false.B)
      dut.io.channelState.bits.addr.expect(1.U)
    }
  }
}
