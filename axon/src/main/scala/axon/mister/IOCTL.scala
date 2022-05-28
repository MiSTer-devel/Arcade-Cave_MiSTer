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

package axon.mister

import axon.mem._
import chisel3._
import chisel3.util._

/** A flow control interface used to download data into the core. */
class IOCTL extends Bundle {
  /** Download enable */
  val download = Input(Bool())
  /** Upload enable */
  val upload = Input(Bool())
  /** Read enable */
  val rd = Input(Bool())
  /** Write enable */
  val wr = Input(Bool())
  /** Asserted when the core isn't ready to proceed with the request */
  val waitReq = Output(Bool())
  /** Index */
  val index = Input(UInt(IOCTL.INDEX_WIDTH.W))
  /** Address bus */
  val addr = Input(UInt(IOCTL.ADDR_WIDTH.W))
  /** Input data bus */
  val din = Output(Bits(IOCTL.DATA_WIDTH.W))
  /** Output data bus */
  val dout = Input(Bits(IOCTL.DATA_WIDTH.W))

  /** Converts ROM data to an asynchronous write-only memory interface. */
  def rom: AsyncWriteMemIO = {
    val writeEnable = download && this.index === IOCTL.ROM_INDEX.U
    val mem = Wire(AsyncWriteMemIO(IOCTL.ADDR_WIDTH, IOCTL.DATA_WIDTH))
    mem.wr := writeEnable && wr
    waitReq := writeEnable && mem.waitReq
    mem.addr := addr
    mem.mask := Fill(mem.maskWidth, 1.U)
    mem.din := dout
    din := 0.U
    mem
  }

  /** Converts NVRAM data to an asynchronous read-write memory interface. */
  def nvram: AsyncReadWriteMemIO = {
    val readEnable = upload && this.index === IOCTL.NVRAM_INDEX.U
    val writeEnable = download && this.index === IOCTL.NVRAM_INDEX.U
    val mem = Wire(AsyncReadWriteMemIO(IOCTL.ADDR_WIDTH, IOCTL.DATA_WIDTH))
    mem.rd := readEnable && rd
    mem.wr := writeEnable && wr
    waitReq := (readEnable || writeEnable) && mem.waitReq
    mem.addr := addr
    mem.mask := Fill(mem.maskWidth, 1.U)
    mem.din := dout
    din := RegEnable(mem.dout, mem.valid)
    mem
  }

  /** Converts DIP switch data to a synchronous write-only memory interface. */
  def dips: AsyncWriteMemIO = {
    val writeEnable = download && this.index === IOCTL.DIP_INDEX.U && !addr(IOCTL.ADDR_WIDTH - 1, 3).orR // ignore higher addresses
    val mem = Wire(AsyncWriteMemIO(IOCTL.ADDR_WIDTH, IOCTL.DATA_WIDTH))
    mem.wr := writeEnable && wr
    waitReq := writeEnable && mem.waitReq
    mem.addr := addr
    mem.mask := Fill(mem.maskWidth, 1.U)
    mem.din := dout
    din := 0.U
    mem
  }
}

object IOCTL {
  /** The width of the index */
  val INDEX_WIDTH = 8
  /** The width of the address bus */
  val ADDR_WIDTH = 27
  /** The width of the data bus */
  val DATA_WIDTH = 16
  /** ROM data index */
  val ROM_INDEX = 0
  /** Game index */
  val GAME_INDEX = 1
  /** NVRAM index */
  val NVRAM_INDEX = 2
  /** DIP switch index */
  val DIP_INDEX = 254

  def apply() = new IOCTL
}
