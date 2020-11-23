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
import chisel3.util._

/**
 * Produces audio samples by processing sample data using a sequence of steps.
 *
 * The audio pipeline steps are:
 *
 *   - Check whether PCM data is required
 *   - Load PCM data
 *   - Decode PCM data
 *   - Interpolate sample values
 *   - Apply level
 *   - Apply pan
 *   - Output audio sample
 *
 * @param config The YMZ280B configuration.
 */
class AudioPipeline(config: YMZ280BConfig) extends Module {
  val io = IO(new Bundle {
    /** Input port */
    val in = DeqIO(new Bundle {
      /** Pipeline state */
      val state = new AudioPipelineState(config)
      /** Pitch */
      val pitch = UInt(config.pitchWidth.W)
      /** Level */
      val level = UInt(config.levelWidth.W)
      /** Pan */
      val pan = UInt(config.panWidth.W)
    })
    /** Output port */
    val out = ValidIO(new Bundle {
      /** Pipeline state */
      val state = new AudioPipelineState(config)
      /** Audio */
      val audio = new Audio(config.sampleWidth)
    })
    /** PCM data port */
    val pcmData = DeqIO(Bits(config.adpcmDataWidth.W))
    /** Debug port */
    val debug = new Bundle {
      val idle = Output(Bool())
      val check = Output(Bool())
      val fetch = Output(Bool())
      val decode = Output(Bool())
      val interpolate = Output(Bool())
      val level = Output(Bool())
      val pan = Output(Bool())
      val done = Output(Bool())
    }
  })

  // States
  val idleState :: checkState :: fetchState :: decodeState :: interpolateState :: levelState :: panState :: doneState :: Nil = Enum(8)

  // Registers
  val stateReg = RegInit(idleState)
  val inputReg = RegEnable(io.in.bits, stateReg === idleState && io.in.valid)
  val sampleReg = Reg(SInt(config.sampleWidth.W))
  val audioReg = Reg(new Audio(config.sampleWidth))
  val pcmDataReg = RegEnable(io.pcmData.bits, stateReg === fetchState && io.pcmData.valid)

  // Control signals
  val underflow = inputReg.state.underflow
  val ready = stateReg === checkState && underflow

  // ADPCM decoder
  val adpcm = Module(new ADPCM(
    sampleWidth = config.sampleWidth,
    dataWidth = config.adpcmDataWidth
  ))
  adpcm.io.data := pcmDataReg
  adpcm.io.in.step := inputReg.state.adpcmStep
  adpcm.io.in.sample := inputReg.state.samples(1)

  // Linear interpolator
  val lerp = Module(new LERP(
    sampleWidth = config.sampleWidth,
    indexWidth = config.lerpIndexWidth
  ))
  lerp.io.samples := inputReg.state.samples
  lerp.io.index := inputReg.state.lerpIndex

  // Decode ADPCM data
  when(stateReg === decodeState) {
    inputReg.state.adpcm(adpcm.io.out.step, adpcm.io.out.sample)
  }

  // Interpolate sample values
  when(stateReg === interpolateState) {
    inputReg.state.interpolate(inputReg.pitch)
    sampleReg := lerp.io.out
  }

  // Apply level
  when(stateReg === levelState) {
    sampleReg := sampleReg*(inputReg.level+&1.U) >> 8
  }

  // Apply pan
  when(stateReg === panState) {
    val s = sampleReg
    val t = inputReg.pan(2, 0)
    val left = WireInit(s)
    val right = WireInit(s)
    when(inputReg.pan > 8.U) {
      left := s*(~t).asUInt >> 3
    }.elsewhen(inputReg.pan < 7.U) {
      right := s*t >> 3
    }
    audioReg := Audio(left, right)
  }

  // FSM
  switch(stateReg) {
    // Wait for a request
    is(idleState) {
      when(io.in.valid) { stateReg := checkState }
    }

    // Check whether PCM data is required
    is(checkState) { stateReg := Mux(underflow, fetchState, interpolateState) }

    // Fetch sample data
    is(fetchState) {
      when(io.pcmData.valid) { stateReg := decodeState }
    }

    // Decode ADPCM sample
    is(decodeState) { stateReg := interpolateState }

    // Interpolate sample
    is(interpolateState) { stateReg := levelState }

    // Apply level
    is(levelState) { stateReg := panState }

    // Apply pan
    is(panState) { stateReg := doneState }

    // Done
    is(doneState) { stateReg := idleState }
  }

  // Outputs
  io.in.ready := stateReg === idleState
  io.out.valid := stateReg === doneState
  io.out.bits.state := inputReg.state
  io.out.bits.audio := audioReg
  io.pcmData.ready := ready
  io.debug.idle := stateReg === idleState
  io.debug.check := stateReg === checkState
  io.debug.fetch := stateReg === fetchState
  io.debug.decode := stateReg === decodeState
  io.debug.interpolate := stateReg === interpolateState
  io.debug.level := stateReg === levelState
  io.debug.pan := stateReg === panState
  io.debug.done := stateReg === doneState

  printf(p"AudioPipeline(state: $stateReg, pcmData: $pcmDataReg, pipelineState: ${inputReg.state})\n")
}
