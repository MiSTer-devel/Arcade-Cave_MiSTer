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

/** Represents the state of a channel. */
class ChannelState(private val config: YMZ280BConfig) extends Bundle {
  /** Asserted when the channel is enabled */
  val enable = Bool()
  /** Asserted when the channel is playing a sample */
  val active = Bool()
  /** Asserted when the channel has reached the end address */
  val done = Bool()
  /** Asserted when the channel is processing the high ADPCM nibble */
  val nibble = Bool()
  /** Sample address */
  val addr = UInt(config.memAddrWidth.W)
  /** Audio pipeline state */
  val audioPipelineState = new AudioPipelineState(config)

  /** Starts the channel at the given address. */
  def start(startAddr: UInt) = {
    enable := true.B
    active := true.B
    done := false.B
    addr := startAddr
    audioPipelineState := AudioPipelineState.default(config)
  }

  /** Stops the channel. */
  def stop() = {
    enable := false.B
    active := false.B
    done := false.B
  }

  /**
   * Moves the channel to the next address. If the channel has reached the end address, then the
   * done flag is asserted.
   *
   * @param channelReg The channel register.
   */
  def nextAddr(channelReg: ChannelReg) = {
    when(channelReg.flags.loop && addr === channelReg.loopEndAddr) {
      addr := channelReg.loopStartAddr
    }.elsewhen(addr =/= channelReg.endAddr) {
      addr := addr + 1.U
    }.otherwise {
      active := false.B
      done := true.B
    }
  }

  /** Clears the done flag. */
  def clearDone() = {
    done := false.B
  }
}

object ChannelState {
  /** Returns the default channel state. */
  def default(config: YMZ280BConfig): ChannelState = {
    val state = Wire(new ChannelState(config))
    state.enable := false.B
    state.active := false.B
    state.done := false.B
    state.nibble := false.B
    state.addr := 0.U
    state.audioPipelineState := AudioPipelineState.default(config)
    state
  }
}
