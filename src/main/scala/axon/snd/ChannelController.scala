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

import axon.mem.ReadMemIO
import chisel3._
import chisel3.util._
import axon.util.Counter

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
    val mem = ReadMemIO(config.memAddrWidth, config.memDataWidth)
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
  val initState :: idleState :: readState :: latchState :: checkState :: readyState :: processState :: writeState :: nextState :: doneState :: Nil = Enum(10)

  // Registers
  val stateReg = RegInit(initState)
  val accumulatorReg = Reg(new Audio(config.sampleWidth))
  val channelStateReg = Reg(new ChannelState(config))

  // Counters
  val (channelCounterValue, channelCounterWrap) = Counter.static(config.numChannels, enable = stateReg === initState || stateReg === nextState)
  val (_, outputCounterWrap) = Counter.static((config.clockFreq/config.sampleFreq).round.toInt)

  // Register aliases
  val channelReg = io.channelRegs(channelCounterValue)

  // Channel state memory
  val mem = SyncReadMem(config.numChannels, new ChannelState(config))

  // Audio pipeline
  val audioPipeline = Module(new AudioPipeline(config))
  audioPipeline.io.in.valid := stateReg === readyState
  audioPipeline.io.in.bits.state := channelStateReg.audioPipelineState
  audioPipeline.io.in.bits.pitch := channelReg.pitch
  audioPipeline.io.in.bits.level := channelReg.level
  audioPipeline.io.in.bits.pan := channelReg.pan

  // Control signals
  val start = !channelStateReg.enable && !channelStateReg.done && channelReg.flags.keyOn
  val stop = !channelReg.flags.keyOn
  val active = channelStateReg.enable || start
  val pcmFetch = stateReg === processState && audioPipeline.io.pcmData.ready
  val pipelineValid = stateReg === processState && audioPipeline.io.out.valid

  // PCM data is valid during the clock cycle following a fetch
  audioPipeline.io.pcmData.valid := RegNext(pcmFetch)
  audioPipeline.io.pcmData.bits := Mux(channelStateReg.nibble, io.mem.dout(7, 4), io.mem.dout(3, 0))

  // Initialize channel states
  when(stateReg === initState) { mem.write(channelCounterValue, ChannelState.default(config)) }

  // Clear accumulator
  when(stateReg === idleState) { accumulatorReg := Audio.zero }

  // Read channel state from memory
  val channelState = mem.read(channelCounterValue, stateReg === readState)

  // Latch channel state
  when(stateReg === latchState) { channelStateReg := channelState }

  // Start/stop channel
  when(stateReg === checkState) {
    when(start) {
      channelStateReg.start(channelReg.startAddr)
    }.elsewhen(stop) {
      channelStateReg.stop()
    }
  }

  when(pcmFetch) {
    // Toggle nibble flag
    channelStateReg.nibble := !channelStateReg.nibble

    // Update sample address after the high nibble has been fetched
    when(channelStateReg.nibble) {
      val done = channelStateReg.nextAddr(channelReg)
      when(done) { channelStateReg.markAsDone() }
    }
  }

  when(pipelineValid) {
    // Sum pipeline audio output with the accumulator
    accumulatorReg := accumulatorReg + audioPipeline.io.out.bits.audio

    // Update pipeline state
    channelStateReg.audioPipelineState := audioPipeline.io.out.bits.state
  }

  // Write channels state to memory
  when(stateReg === writeState) { mem.write(channelCounterValue, channelStateReg) }

  // FSM
  switch(stateReg) {
    // Initialize channel states
    is(initState) {
      when(outputCounterWrap) { stateReg := idleState }
    }

    // Clear accumulator
    is(idleState) {
      when(io.enable) { stateReg := readState }
    }

    // Read channel state from memory
    is(readState) { stateReg := latchState }

    // Latch channel state
    is(latchState) { stateReg := checkState }

    // Check whether the channel is active
    is(checkState) { stateReg := Mux(active, readyState, writeState) }

    // Wait for the pipeline to be ready
    is(readyState) {
      when(audioPipeline.io.in.ready) { stateReg := processState }
    }

    // Process channel through audio pipeline
    is(processState) {
      when(audioPipeline.io.out.valid) { stateReg := writeState }
    }

    // Write channel state to memory
    is(writeState) { stateReg := nextState }

    // Increment channel index
    is(nextState) { stateReg := Mux(channelCounterWrap, doneState, readState) }

    // All channels processed, write audio output
    is(doneState) {
      when(outputCounterWrap) { stateReg := idleState }
    }
  }

  // Outputs
  io.channelState.valid := stateReg === writeState
  io.channelState.bits := channelStateReg
  io.channelIndex := channelCounterValue
  io.audio.valid := outputCounterWrap
  io.audio.bits := accumulatorReg
  io.mem.addr := channelStateReg.addr
  io.mem.rd := pcmFetch
  io.debug.init := stateReg === initState
  io.debug.idle := stateReg === idleState
  io.debug.read := stateReg === readState
  io.debug.latch := stateReg === latchState
  io.debug.check := stateReg === checkState
  io.debug.ready := stateReg === readyState
  io.debug.process := stateReg === processState
  io.debug.write := stateReg === writeState
  io.debug.next := stateReg === nextState
  io.debug.done := stateReg === doneState

  printf(p"ChannelController(state: $stateReg, index: $channelCounterValue ($channelCounterWrap), channelState: $channelStateReg, audio: $accumulatorReg ($outputCounterWrap))\n")
}
