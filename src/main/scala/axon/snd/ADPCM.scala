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
import chisel3._

/**
 * Decodes adaptive differential pulse-code modulation (ADPCM) samples.
 *
 * @param sampleWidth The width of the sample words.
 * @param dataWidth   The width of the ADPCM data.
 * @see https://en.wikipedia.org/wiki/Adaptive_differential_pulse-code_modulation
 */
class ADPCM(sampleWidth: Int = 16, dataWidth: Int = 4) extends Module {
  /** Calculate the step size for the current ADPCM value */
  val STEP_LUT = VecInit(230.S, 230.S, 230.S, 230.S, 307.S, 409.S, 512.S, 614.S)

  /** Calculate the delta for the current sample value */
  val DELTA_LUT = VecInit.tabulate(16) { n =>
    val value = (n & 7) * 2 + 1
    if (n >= 8) (-value).S else value.S
  }

  val io = IO(new Bundle {
    /** ADPCM data */
    val data = Input(Bits(dataWidth.W))
    /** Input port */
    val in = Input(new Bundle {
      /** ADPCM step value */
      val step = SInt(sampleWidth.W)
      /** ADPCM sample value */
      val sample = SInt(sampleWidth.W)
    })
    /** Output port */
    val out = Output(new Bundle {
      /** ADPCM step value */
      val step = SInt(sampleWidth.W)
      /** ADPCM sample value */
      val sample = SInt(sampleWidth.W)
    })
  })

  // Calculate step value
  val step = ((io.in.step * STEP_LUT(io.data)) >> 8).asSInt
  io.out.step := Util.clamp(step, 127, 24576)

  // Calculate sample value
  val delta = ((io.in.step * DELTA_LUT(io.data)) >> 3).asSInt
  io.out.sample := Util.clamp(io.in.sample +& delta, -32768, 32767)
}
