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

package arcadia.mem.sdram

import arcadia.mem.BusConfig
import chisel3._
import chisel3.util.log2Ceil

/**
 * Represents the SDRAM configuration.
 *
 * The default values used here may not work for every device, so be sure to check the datasheet.
 *
 * @param clockFreq      The SDRAM clock frequency (Hz).
 * @param bankWidth      The width of the bank address.
 * @param rowWidth       The width of the row address.
 * @param colWidth       The width of the column address.
 * @param dataWidth      The width of the data bus.
 * @param burstLength    The number of words to be transferred during a read/write.
 * @param burstType      The burst type (0=sequential, 1=interleaved).
 * @param casLatency     The delay in clock cycles, between the start of a read operation and the
 *                       first data value transferred.
 * @param writeBurstMode The write burst mode (0=burst, 1=single).
 * @param tINIT          The device initialization delay (ns).
 * @param tMRD           The mode register cycle time (ns).
 * @param tRC            The row cycle time (ns).
 * @param tRCD           The RAS to CAS delay (ns).
 * @param tRP            The precharge to activate delay (ns).
 * @param tWR            The write recovery time (ns).
 * @param tREFI          The refresh interval (ns).
 */
case class Config(clockFreq: Double,
                  bankWidth: Int = 2,
                  rowWidth: Int = 13,
                  colWidth: Int = 9,
                  dataWidth: Int = 16,
                  burstLength: Int = 1,
                  burstType: Int = 0,
                  casLatency: Int = 2,
                  writeBurstMode: Int = 0,
                  tINIT: Double = 200_000,
                  tMRD: Double = 12,
                  tRC: Double = 60,
                  tRCD: Double = 18,
                  tRP: Double = 18,
                  tWR: Double = 12,
                  tREFI: Double = 7800) extends BusConfig {
  /** The width of the address bus (i.e. the byte width of all banks, rows, and columns). */
  val addrWidth = bankWidth + rowWidth + colWidth + 1
  /** The SDRAM clock period (ns). */
  val clockPeriod = 1 / clockFreq * 1_000_000_000D
  /** The number of clock cycles to wait before selecting the device. */
  val deselectWait = (tINIT / clockPeriod).ceil.toLong
  /** The number of clock cycles to wait for a PRECHARGE command. */
  val prechargeWait = (tRP / clockPeriod).ceil.toLong
  /** The number of clock cycles to wait for a MODE command. */
  val modeWait = (tMRD / clockPeriod).ceil.toLong
  /** The number of clock cycles to wait for an ACTIVE command. */
  val activeWait = (tRCD / clockPeriod).ceil.toLong
  /** The number of clock cycles to wait for a READ command. */
  val readWait = casLatency + burstLength
  /** The number of clock cycles to wait for a WRITE command. */
  val writeWait = burstLength + ((tWR + tRP) / clockPeriod).ceil.toLong
  /** The number of clock cycles to wait for a REFRESH command. */
  val refreshWait = (tRC / clockPeriod).ceil.toLong
  /** The number of clock cycles between REFRESH commands. */
  val refreshInterval = (tREFI / clockPeriod).floor.toLong
  /** The number of clock cycles to wait during initialization. */
  val initWait = deselectWait + prechargeWait + refreshWait + refreshWait
  /** The maximum value of the wait counter. */
  val waitCounterMax = 1 << log2Ceil(Seq(initWait, readWait, writeWait).max)
  /** The maximum value of the refresh counter. */
  val refreshCounterMax = 1 << log2Ceil(refreshInterval)

  /** The opcode to configure the SDRAM device. */
  def opcode: UInt =
    0.U(3.W) ## // unused
      writeBurstMode.U(1.W) ##
      0.U(2.W) ## // unused
      casLatency.U(3.W) ##
      burstType.U(1.W) ##
      log2Ceil(burstLength).U(3.W)
}
