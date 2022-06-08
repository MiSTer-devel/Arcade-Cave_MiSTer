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

import chiseltest._
import org.scalatest._
import flatspec.AnyFlatSpec
import matchers.should.Matchers

class AudioMixerTest extends AnyFlatSpec with ChiselScalatestTester with Matchers {
  it should "sum the audio inputs" in {
    test(new AudioMixer(16, Seq(ChannelConfig(16, 1), ChannelConfig(16, 0.5), ChannelConfig(16, 0.25)))) { dut =>
      dut.io.in(0).poke(1)
      dut.io.in(1).poke(2)
      dut.io.in(2).poke(4)
      dut.clock.step()
      dut.io.out.expect(3)
    }
  }

  it should "clips the audio output (8-bit)" in {
    test(new AudioMixer(8, Seq(ChannelConfig(8, 1), ChannelConfig(8, 1)))) { dut =>
      dut.io.in(0).poke(127)
      dut.io.in(1).poke(1)
      dut.clock.step()
      dut.io.out.expect(127)

      dut.io.in(0).poke(-128)
      dut.io.in(1).poke(-1)
      dut.clock.step()
      dut.io.out.expect(-128)
    }
  }

  it should "clips the audio output (16-bit)" in {
    test(new AudioMixer(16, Seq(ChannelConfig(16, 1), ChannelConfig(16, 1)))) { dut =>
      dut.io.in(0).poke(32767)
      dut.io.in(1).poke(1)
      dut.clock.step()
      dut.io.out.expect(32767)

      dut.io.in(0).poke(-32768)
      dut.io.in(1).poke(-1)
      dut.clock.step()
      dut.io.out.expect(-32768)
    }
  }

  it should "converts all samples to the output width" in {
    test(new AudioMixer(9, Seq(ChannelConfig(8, 1)))) { dut =>
      dut.io.in(0).poke(127)
      dut.clock.step()
      dut.io.out.expect(254)
    }
  }
}
