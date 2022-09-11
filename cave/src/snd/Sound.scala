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

package cave.snd

import arcadia.mem._
import arcadia.snd._
import cave._
import chisel3._
import chisel3.util._

/**
 * Represents the sound PCB.
 *
 * Earlier games tend to use one (or more) OKIM6295 ADPCM decoder chips, while later games rely on
 * a single YMZ280B ADPCM decoder chip.
 */
class Sound extends Module {
  val io = IO(new Bundle {
    /** Game config port */
    val gameConfig = Input(GameConfig())
    /** Control port */
    val ctrl = SoundCtrlIO()
    /** Sound ROM port */
    val rom = Vec(Config.SOUND_ROM_COUNT, new SoundRomIO)
    /** Audio port */
    val audio = Output(SInt(Config.AUDIO_SAMPLE_WIDTH.W))
  })

  // NMK112 banking controller
  val nmk = Module(new NMK112)
  nmk.io.cpu <> io.ctrl.nmk
  nmk.io.mask := 1.U // disable phrase table bank switching for chip 0 (background music)

  // OKIM6295 ADPCM decoder
  val oki = 0.until(Sound.OKI_COUNT).map { i =>
    val oki = Module(new OKIM6295(Config.okiConfig(i)))
    oki.io.cpu <> io.ctrl.oki(i)
    oki
  }

  // Transform OKIM62695 sound ROM addresses using the banking controller
  val okiRom = Seq(
    oki(0).io.rom.mapAddr(nmk.transform(0)),
    oki(1).io.rom.mapAddr(nmk.transform(1))
  )

  // YMZ280B ADPCM decoder
  val ymz = Module(new YMZ280B(Config.ymzConfig))
  ymz.io.cpu <> io.ctrl.ymz
  ymz.io.rom <> io.rom(0)
  io.ctrl.irq := ymz.io.irq

  // Mux sound ROM port 0
  io.rom(0) <> AsyncReadMemIO.mux1H(Seq(
    (io.gameConfig.sound(0).device === SoundDevice.YMZ280B.U) -> ymz.io.rom,
    (io.gameConfig.sound(0).device === SoundDevice.OKIM6259.U) -> okiRom(0)
  ))

  // Set sound ROM port 1
  io.rom(1) <> okiRom(1)

  // Audio mixer
  io.audio := AudioMixer.sum(Config.AUDIO_SAMPLE_WIDTH,
    RegEnable(ymz.io.audio.bits.left, ymz.io.audio.valid) -> 1,
    RegEnable(oki(0).io.audio.bits, oki(0).io.audio.valid) -> 1.6,
    RegEnable(oki(1).io.audio.bits, oki(1).io.audio.valid) -> 1
  )
}

object Sound {
  /** The number of OKIM6295 chips */
  val OKI_COUNT = 2
}
