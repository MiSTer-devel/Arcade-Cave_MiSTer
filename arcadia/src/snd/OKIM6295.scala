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

package arcadia.snd

import arcadia.clk.ClockDivider
import arcadia.mem.{AsyncReadMemIO, ReadWriteMemIO}
import chisel3._
import chisel3.util._

/**
 * Represents the OKIM6295 configuration.
 *
 * @param clockFreq    The system clock frequency (Hz).
 * @param sampleFreq   The sample clock frequency (Hz).
 * @param sampleWidth  The width of the sample words.
 * @param memAddrWidth The width of the memory address bus.
 * @param memDataWidth The width of the memory data bus.
 * @param cpuAddrWidth The width of the CPU address bus.
 * @param cpuDataWidth The width of the CPU data bus.
 */
case class OKIM6295Config(clockFreq: Double,
                         sampleFreq: Double,
                         sampleWidth: Int = 14,
                         memAddrWidth: Int = 18,
                         memDataWidth: Int = 8,
                         cpuAddrWidth: Int = 1,
                         cpuDataWidth: Int = 8)

/**
 * The OKIM6295 is a 4-channel ADPCM decoder.
 *
 * It receives commands from the CPU and plays samples stored in an external ROM.
 *
 * @note This module wraps jotego's JT6295 implementation.
 * @see https://github.com/jotego/jt6295
 */
class OKIM6295(config: OKIM6295Config) extends Module {
  val io = IO(new Bundle {
    /** CPU port */
    val cpu = Flipped(ReadWriteMemIO(config.cpuAddrWidth, config.cpuDataWidth))
    /** Sound ROM port */
    val rom = AsyncReadMemIO(config.memAddrWidth, config.memDataWidth)
    /** Audio output port */
    val audio = ValidIO(SInt(config.sampleWidth.W))
  })

  class JT6295 extends BlackBox {
    val io = IO(new Bundle {
      val rst = Input(Bool())
      val clk = Input(Bool())
      val cen = Input(Bool())
      val ss = Input(Bool())
      val wrn = Input(Bool())
      val din = Input(Bits(config.cpuDataWidth.W))
      val dout = Output(Bits(config.cpuDataWidth.W))
      val rom_addr = Output(UInt(config.memAddrWidth.W))
      val rom_data = Input(Bits(config.memDataWidth.W))
      val rom_ok = Input(Bool())
      val sound = Output(SInt(config.sampleWidth.W))
      val sample = Output(Bool())
    })

    override def desiredName = "jt6295"
  }

  val adpcm = Module(new JT6295)
  adpcm.io.rst := reset.asBool
  adpcm.io.clk := clock.asBool
  adpcm.io.cen := ClockDivider(config.clockFreq / config.sampleFreq)
  adpcm.io.ss := true.B

  adpcm.io.wrn := !io.cpu.wr
  adpcm.io.din := io.cpu.din
  io.cpu.dout := adpcm.io.dout

  io.rom.rd := true.B // read-only
  io.rom.addr := adpcm.io.rom_addr
  adpcm.io.rom_data := io.rom.dout
  adpcm.io.rom_ok := io.rom.valid

  io.audio.valid := adpcm.io.sample
  io.audio.bits := adpcm.io.sound
}
