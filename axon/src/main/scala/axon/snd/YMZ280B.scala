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

import axon.mem.{AsyncReadMemIO, ReadWriteMemIO}
import axon.snd.ymz._
import chisel3._
import chisel3.util._

/**
 * Represents the YMZ280B configuration.
 *
 * @param clockFreq      The system clock frequency (Hz).
 * @param sampleFreq     The sample clock frequency (Hz).
 * @param sampleWidth    The width of the sample words.
 * @param adpcmDataWidth The width of the ADPCM data.
 * @param memAddrWidth   The width of the memory address bus.
 * @param memDataWidth   The width of the memory data bus.
 * @param cpuAddrWidth   The width of the CPU address bus.
 * @param cpuDataWidth   The width of the CPU data bus.
 * @param numChannels    The number of channels.
 */
case class YMZ280BConfig(clockFreq: Double,
                         sampleFreq: Double = 44100,
                         sampleWidth: Int = 16,
                         adpcmDataWidth: Int = 4,
                         memAddrWidth: Int = 24,
                         memDataWidth: Int = 8,
                         cpuAddrWidth: Int = 1,
                         cpuDataWidth: Int = 8,
                         numChannels: Int = 8) {
  /** The number of registers in the register file */
  val numRegs = 256
  /** The width of the pitch value */
  val pitchWidth = 8
  /** The width of the level value */
  val levelWidth = 8
  /** The width of the pan value */
  val panWidth = 4
  /** The maximum frequency */
  val maxFreq = 44100D
  /** The width of the linear interpolation index value */
  val lerpIndexWidth = (pitchWidth + sampleFreq / maxFreq).ceil.toInt
  /** The internal sample width */
  val internalSampleWidth = 17
}

/**
 * The YMZ280B is a PCM/ADPCM decoder that can play up to eight audio channels simultaneously.
 *
 * @param config The YMZ280B configuration.
 */
class YMZ280B(config: YMZ280BConfig) extends Module {
  val io = IO(new Bundle {
    /** CPU port */
    val cpu = Flipped(ReadWriteMemIO(config.cpuAddrWidth, config.cpuDataWidth))
    /** ROM port */
    val rom = AsyncReadMemIO(config.memAddrWidth, config.memDataWidth)
    /** Audio output */
    val audio = ValidIO(new Audio(config.sampleWidth))
    /** IRQ */
    val irq = Output(Bool())
    /** Debug signals */
    val debug = Output(new Bundle {
      /** Channel descriptors */
      val channels = Vec(config.numChannels, new ChannelReg)
      /** Utility register */
      val utilReg = new UtilReg
      /** Status register */
      val statusReg = Bits(config.cpuDataWidth.W)
    })
  })

  // Registers
  val addrReg = RegInit(0.U)
  val dataReg = RegInit(0.U)
  val statusReg = RegInit(0.U)
  val registerFile = RegInit(VecInit.tabulate(config.numRegs) { _ => 0.U(config.cpuDataWidth.W) })

  // Map register file to channel descriptors
  val channelRegs = WireInit(VecInit.tabulate(config.numChannels) { ChannelReg.fromRegisterFile(registerFile) })

  // Map register file to utility register
  val utilReg = WireInit(UtilReg.fromRegisterFile(registerFile))

  // Channel controller
  val channelCtrl = Module(new ChannelController(config))
  channelCtrl.io.regs := channelRegs
  channelCtrl.io.enable := utilReg.flags.keyOnEnable
  channelCtrl.io.audio <> io.audio
  channelCtrl.io.rom <> io.rom

  // Control signals
  val writeAddr = io.cpu.wr && !io.cpu.addr(0)
  val writeData = io.cpu.wr && io.cpu.addr(0)
  val readStatus = io.cpu.rd && io.cpu.addr(0)

  // Write to address register
  when(writeAddr) { addrReg := io.cpu.din }

  // Write to register file
  when(writeData) { registerFile(addrReg) := io.cpu.din }

  // Mark current channel as done in the status register
  when(channelCtrl.io.done) { statusReg := statusReg.bitSet(channelCtrl.io.index, true.B) }

  // Read and clear the status register
  when(readStatus) {
    dataReg := statusReg
    statusReg := 0.U
  }

  // Outputs
  io.cpu.dout := dataReg
  io.irq := utilReg.flags.irqEnable && (statusReg & utilReg.irqMask).orR
  io.debug.channels := channelRegs
  io.debug.utilReg := utilReg
  io.debug.statusReg := statusReg

  printf(p"YMZ280B(addrReg: $addrReg, status: $statusReg)\n")
}
