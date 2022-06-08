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

import axon.Util
import chisel3._
import chisel3.util.MixedVec

/**
 * Represents a channel configuration.
 *
 * @param width The width of the input samples.
 * @param gain  The gain adjustment.
 */
case class ChannelConfig(width: Int, gain: Double) {
  /** The gain represented as a fixed point signed integer */
  val fixedPointGain = (gain * (1 << AudioMixer.GAIN_PRECISION)).round.S
}

/**
 * The mixer sums multiple audio inputs into a single audio output, applying gain and clipping.
 *
 * @param width    The width of the output samples.
 * @param channels The channel configurations.
 */
class AudioMixer(width: Int, channels: Seq[ChannelConfig]) extends Module {
  val io = IO(new Bundle {
    /** Audio input port */
    val in = Input(MixedVec(channels.map { c => SInt(c.width.W) }))
    /** Audio output port */
    val out = Output(SInt(width.W))
  })

  // Sum audio inputs
  val sum = io.in
    .zip(channels).map { case (in, config) => AudioMixer.scale(in, config.width, width) * config.fixedPointGain }
    .reduceLeft((a, b) => a +& b)

  // Clip audio output
  io.out := {
    val clipped = (sum >> AudioMixer.GAIN_PRECISION).asSInt
    val maxSample = 1 << (width - 1)
    RegNext(Util.clamp(clipped, -maxSample, maxSample - 1))
  }
}

object AudioMixer {
  /** The number of fractional bits for gain values */
  val GAIN_PRECISION = 4

  /**
   * Scales a sample to a given width.
   *
   * @param s    The sample value.
   * @param from The original width.
   * @param to   The new width.
   * @return A signed integer.
   */
  private def scale(s: SInt, from: Int, to: Int): SInt = (s << (to - from)).asSInt

  /**
   * Sums the given audio channels.
   *
   * @param width The width of the output samples.
   * @param in    A list of tuples of audio channels and gain values.
   * @return A signed integer.
   */
  def sum(width: Int, in: (SInt, Double)*): SInt = {
    val config = in.map { case (channel, gain) => ChannelConfig(channel.getWidth, gain) }
    val mixer = Module(new AudioMixer(width, config))
    mixer.io.in := in.map(_._1)
    mixer.io.out
  }
}
