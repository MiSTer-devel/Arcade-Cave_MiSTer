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

package arcadia.snd.ymz

import arcadia.Util
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
