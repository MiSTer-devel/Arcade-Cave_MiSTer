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

package arcadia.mem.buffer

import arcadia.mem._
import arcadia.util.Counter
import chisel3._
import chisel3.util._

/**
 * Buffers data written to the input port, until there is enough data to trigger a burst on the
 * output port.
 *
 * @param config The buffer configuration.
 */
class BurstBuffer(config: Config) extends Module {
  val io = IO(new Bundle {
    /** Input port */
    val in = Flipped(AsyncWriteMemIO(config.inAddrWidth, config.inDataWidth))
    /** Output port */
    val out = BurstWriteMemIO(config.outAddrWidth, config.outDataWidth)
  })

  // Registers
  val writePendingReg = RegInit(false.B)
  val lineReg = Reg(new Line(config))
  val addrReg = Reg(UInt(config.inAddrWidth.W))

  // Control signals
  val latch = io.in.wr && !writePendingReg
  val effectiveWrite = writePendingReg && !io.out.waitReq

  // Counters
  val (wordCounter, wordCounterWrap) = Counter.static(config.inWords, enable = latch)
  val (burstCounter, burstCounterWrap) = Counter.static(config.burstLength, enable = effectiveWrite)

  // Toggle write pending register
  when(io.out.burstDone) {
    writePendingReg := false.B
  }.elsewhen(wordCounterWrap) {
    writePendingReg := true.B
  }

  // Latch input words
  when(latch) {
    val words = WireInit(lineReg.inWords)
    words(wordCounter) := io.in.din
    lineReg.words := words.asTypeOf(chiselTypeOf(lineReg.words))
    addrReg := io.in.addr
  }

  // Outputs
  io.in.waitReq := writePendingReg
  io.out.wr := writePendingReg
  io.out.burstLength := config.burstLength.U
  io.out.addr := (addrReg >> log2Ceil(config.outBytes)) << log2Ceil(config.outBytes)
  io.out.din := lineReg.outWords(burstCounter)
  io.out.mask := Fill(config.outBytes, 1.U)

  printf(p"BurstBuffer(busy: $writePendingReg, addr: ${ io.out.addr }, wordCounter: $wordCounter ($wordCounterWrap), burstCounter: $burstCounter ($burstCounterWrap), line: 0x${ Hexadecimal(lineReg.words.asUInt) })\n")
}
