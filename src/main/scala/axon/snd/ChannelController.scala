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

import axon.Util
import axon.mem.AsyncReadMemIO
import axon.util.Counter
import chisel3._
import chisel3.util._

/**
 * Manages the channels.
 *
 * The channel controller processes each channel through the audio pipeline and sums their audio
 * outputs.
 *
 * @param config The YMZ280B configuration.
 */
class ChannelController(config: YMZ280BConfig) extends Module {
  val io = IO(new Bundle {
    /** Asserted when the channel controller is enabled */
    val enable = Input(Bool())
    /** Channel registers port */
    val channelRegs = Input(Vec(config.numChannels, new ChannelReg))
    /** Channel index */
    val channelIndex = Output(UInt())
    /** Channel state port */
    val channelState = ValidIO(new ChannelState(config))
    /** Audio output */
    val audio = ValidIO(new Audio(config.sampleWidth))
    /** External memory port */
    val mem = AsyncReadMemIO(config.memAddrWidth, config.memDataWidth)
    /** Debug port */
    val debug = new Bundle {
      val init = Output(Bool())
      val idle = Output(Bool())
      val read = Output(Bool())
      val latch = Output(Bool())
      val check = Output(Bool())
      val ready = Output(Bool())
      val process = Output(Bool())
      val write = Output(Bool())
      val next = Output(Bool())
      val done = Output(Bool())
    }
  })

  // States
  object State {
    val init :: idle :: read :: latch :: check :: ready :: process :: write :: next :: done :: Nil = Enum(10)
  }

  // Registers
  val stateReg = RegInit(State.init)
  val accumulatorReg = Reg(new Audio(config.sampleWidth))
  val channelStateReg = Reg(new ChannelState(config))

  // Counters
  val (channelCounterValue, channelCounterWrap) = Counter.static(config.numChannels, enable = stateReg === State.init || stateReg === State.next)
  val (_, outputCounterWrap) = Counter.static((config.clockFreq / config.sampleFreq).round.toInt)

  // Register aliases
  val channelReg = io.channelRegs(channelCounterValue)

  // Channel state memory
  val channelStateMem = SyncReadMem(config.numChannels, new ChannelState(config))

  // Audio pipeline
  val audioPipeline = Module(new AudioPipeline(config))
  audioPipeline.io.in.valid := stateReg === State.ready
  audioPipeline.io.in.bits.state := channelStateReg.audioPipelineState
  audioPipeline.io.in.bits.pitch := channelReg.pitch
  audioPipeline.io.in.bits.level := channelReg.level
  audioPipeline.io.in.bits.pan := channelReg.pan

  // Control signals
  val start = !channelStateReg.enable && !channelStateReg.done && channelReg.flags.keyOn
  val stop = !channelReg.flags.keyOn
  val active = channelStateReg.enable || start

  // PCM data is valid during the clock cycle following a fetch
  audioPipeline.io.pcmData.valid := io.mem.valid
  audioPipeline.io.pcmData.bits := Mux(channelStateReg.nibble, io.mem.dout(3, 0), io.mem.dout(7, 4))

  // Initialize channel states
  when(stateReg === State.init) { channelStateMem.write(channelCounterValue, ChannelState.default(config)) }

  // Clear accumulator
  when(stateReg === State.idle) { accumulatorReg := Audio.zero }

  // Read channel state from memory
  val channelState = channelStateMem.read(channelCounterValue, stateReg === State.read)

  // Latch channel state
  when(stateReg === State.latch) { channelStateReg := channelState }

  // Start/stop channel
  when(stateReg === State.check) {
    when(start) {
      channelStateReg.start(channelReg.startAddr)
    }.elsewhen(stop) {
      channelStateReg.stop()
    }
  }

  // PCM data has been fetched
  when(audioPipeline.io.pcmData.fire()) {
    // Toggle high/low nibble
    channelStateReg.nibble := !channelStateReg.nibble

    // Move to next sample address after the high nibble has been processed
    when(channelStateReg.nibble) {
      val done = channelStateReg.nextAddr(channelReg)
      when(done) { channelStateReg.markAsDone() }
    }
  }

  // Audio pipeline has produced valid output
  when(audioPipeline.io.out.valid) {
    // Sum pipeline audio output with the accumulator
    accumulatorReg := accumulatorReg + audioPipeline.io.out.bits.audio

    // Update pipeline state
    channelStateReg.audioPipelineState := audioPipeline.io.out.bits.state
  }

  // Write channels state to memory
  when(stateReg === State.write) { channelStateMem.write(channelCounterValue, channelStateReg) }

  // FSM
  switch(stateReg) {
    // Initialize channel states
    is(State.init) {
      when(outputCounterWrap) { stateReg := State.idle }
    }

    // Clear accumulator
    is(State.idle) {
      when(io.enable) { stateReg := State.read }
    }

    // Read channel state from memory
    is(State.read) { stateReg := State.latch }

    // Latch channel state
    is(State.latch) { stateReg := State.check }

    // Check whether the channel is active
    is(State.check) { stateReg := Mux(active, State.ready, State.write) }

    // Wait for the pipeline to be ready
    is(State.ready) {
      when(audioPipeline.io.in.ready) { stateReg := State.process }
    }

    // Process channel through audio pipeline
    is(State.process) {
      when(audioPipeline.io.out.valid) { stateReg := State.write }
    }

    // Write channel state to memory
    is(State.write) { stateReg := State.next }

    // Increment channel index
    is(State.next) { stateReg := Mux(channelCounterWrap, State.done, State.read) }

    // All channels processed, write audio output
    is(State.done) {
      when(outputCounterWrap) { stateReg := State.idle }
    }
  }

  // Outputs
  io.channelState.valid := stateReg === State.write
  io.channelState.bits := channelStateReg
  io.channelIndex := channelCounterValue
  io.audio.valid := outputCounterWrap
  io.audio.bits := accumulatorReg
  io.mem.addr := channelStateReg.addr
  io.mem.rd := Util.rising(audioPipeline.io.pcmData.ready)
  io.debug.init := stateReg === State.init
  io.debug.idle := stateReg === State.idle
  io.debug.read := stateReg === State.read
  io.debug.latch := stateReg === State.latch
  io.debug.check := stateReg === State.check
  io.debug.ready := stateReg === State.ready
  io.debug.process := stateReg === State.process
  io.debug.write := stateReg === State.write
  io.debug.next := stateReg === State.next
  io.debug.done := stateReg === State.done

  printf(p"ChannelController(state: $stateReg, index: $channelCounterValue ($channelCounterWrap), channelState: $channelStateReg, audio: $accumulatorReg ($outputCounterWrap))\n")
}
