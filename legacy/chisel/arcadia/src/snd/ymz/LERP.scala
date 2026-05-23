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

import chisel3._

/**
 * Interpolates sample values.
 *
 * @param sampleWidth The width of the sample words.
 * @param indexWidth  The width of the interpolation index.
 */
class LERP(sampleWidth: Int = 16, indexWidth: Int = 9) extends Module {
  val io = IO(new Bundle {
    /** Input sample values */
    val samples = Input(Vec(2, SInt(sampleWidth.W)))
    /** Interpolation index */
    val index = Input(UInt(indexWidth.W))
    /** Output sample value */
    val out = Output(SInt(sampleWidth.W))
  })

  // Calculate interpolated sample value
  val slope = io.samples(1) -& io.samples(0)
  val offset = io.index * slope
  io.out := offset(sampleWidth + indexWidth - 2, indexWidth - 1).asSInt + io.samples(0)
}
