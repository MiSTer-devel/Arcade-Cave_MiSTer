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

/** Represents the state of an audio pipeline. */
class AudioPipelineState(private val config: YMZ280BConfig) extends Bundle {
  /** Sample values */
  val samples = Vec(2, SInt(config.sampleWidth.W))
  /** Asserted when a new sample is required */
  val underflow = Bool()
  /** ADPCM step value */
  val adpcmStep = SInt(config.sampleWidth.W)
  /** Interpolation index */
  val lerpIndex = UInt(config.lerpIndexWidth.W)
  /** Asserted when the loop step and sample values are cached */
  val loopEnable = Bool()
  /** Cached loop ADPCM step value */
  val loopStep = SInt(config.sampleWidth.W)
  /** Cached loop sample value */
  val loopSample = SInt(config.sampleWidth.W)

  /** Updates the ADPCM state with the given step and sample values. */
  def adpcm(step: SInt, sample: SInt) = {
    adpcmStep := step
    samples := VecInit(samples(1), sample)
  }

  /** Updates the interpolation index with the given pitch value. */
  def interpolate(pitch: UInt) = {
    val index = lerpIndex + pitch + 1.U
    underflow := index.head(1)
    lerpIndex := index.tail(1)
  }
}

object AudioPipelineState {
  /** Returns the default audio pipeline state. */
  def default(config: YMZ280BConfig): AudioPipelineState = {
    val state = Wire(new AudioPipelineState(config))
    state.samples := VecInit(0.S, 0.S)
    state.adpcmStep := 127.S
    state.lerpIndex := 0.U
    state.underflow := true.B
    state.loopEnable := false.B
    state.loopStep := 0.S
    state.loopSample := 0.S
    state
  }
}
