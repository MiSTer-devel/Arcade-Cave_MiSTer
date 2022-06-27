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

import arcadia.Util
import arcadia.mem._
import chisel3._
import chisel3.util._

/**
 * Buffers contiguous words written to the input port. The buffer is flushed as soon as a
 * non-contiguous word is written.
 *
 * @param config The buffer configuration.
 */
class WordBuffer(config: Config) extends Module {
  val io = IO(new Bundle {
    /** Asserted when the buffer is dirty */
    val dirty = Output(Bool())
    /** Input port */
    val in = Flipped(new AsyncWriteMemIO(config.inAddrWidth, config.inDataWidth))
    /** Output port */
    val out = AsyncWriteMemIO(config.outAddrWidth, config.outDataWidth)
  })

  // Registers
  val addrReg = RegInit(0.U(config.outAddrWidth.W))
  val maskReg = RegInit(0.U(config.outBytes.W))
  val lineReg = Reg(new Line(config))

  // Addresses
  val addr = Util.maskBits(io.in.addr, log2Ceil(config.outBytes))
  val byteIndex = io.in.addr(log2Ceil(config.outBytes) - 1, 0)
  val wordIndex = {
    val a = log2Ceil(config.inWords)
    val b = log2Ceil(config.inBytes)
    io.in.addr(a + b - 1, b)
  }

  // Control signals
  val dirty = maskReg.orR
  val latch = io.in.wr && !io.out.waitReq
  val flush = dirty && addr =/= addrReg
  val effectiveFlush = flush && !io.out.waitReq

  // Latch input words
  when(effectiveFlush) {
    maskReg := 0.U
  }.elsewhen(latch) {
    val words = WireInit(lineReg.inWords)
    words(wordIndex) := io.in.din
    addrReg := addr
    maskReg := maskReg | (io.in.mask << byteIndex).asUInt
    lineReg.words := words.asTypeOf(chiselTypeOf(lineReg.words))
  }

  // Outputs
  io.dirty := dirty
  io.in.waitReq := flush
  io.out.wr := flush
  io.out.addr := addrReg
  io.out.mask := maskReg
  io.out.din := lineReg.outWords(0)

  printf(p"WordBuffer(inAddr: 0x${ Hexadecimal(addr) }, outAddr: 0x${ Hexadecimal(io.out.addr) }, mask: ${ Binary(maskReg) }, line: 0x${ Hexadecimal(lineReg.words.asUInt) }, dirty: $dirty)\n")
}
