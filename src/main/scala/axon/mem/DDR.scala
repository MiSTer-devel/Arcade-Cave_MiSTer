/*
 *    __   __     __  __     __         __
 *   /\ "-.\ \   /\ \/\ \   /\ \       /\ \
 *   \ \ \-.  \  \ \ \_\ \  \ \ \____  \ \ \____
 *    \ \_\\"\_\  \ \_____\  \ \_____\  \ \_____\
 *     \/_/ \/_/   \/_____/   \/_____/   \/_____/
 *    ______     ______       __     ______     ______     ______
 *   /\  __ \   /\  == \     /\ \   /\  ___\   /\  ___\   /\__  _\
 *   \ \ \/\ \  \ \  __<    _\_\ \  \ \  __\   \ \ \____  \/_/\ \/
 *    \ \_____\  \ \_____\ /\_____\  \ \_____\  \ \_____\    \ \_\
 *     \/_____/   \/_____/ \/_____/   \/_____/   \/_____/     \/_/
 *
 *  https://joshbassett.info
 *  https://twitter.com/nullobject
 *  https://github.com/nullobject
 *
 *  Copyright (c) 2020 Josh Bassett
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
 */

package axon.mem

import axon.Util
import axon.util.Counter
import chisel3._
import chisel3.util._

/** An interface for reading and writing to DDR memory. */
class DDRIO protected (config: DDRConfig) extends AsyncReadWriteMemIO(config.addrWidth, config.dataWidth) with BurstIO

object DDRIO {
  def apply(config: DDRConfig) = new DDRIO(config: DDRConfig)
}

/**
 * Represents the DDR memory configuration.
 *
 * @param addrWidth The width of the address bus.
 * @param dataWidth The width of the data bus.
 */
case class DDRConfig(addrWidth: Int = 32, dataWidth: Int = 64)

/**
 * Handles reading/writing data to a DDR memory device.
 *
 * @param config The DDR configuration.
 */
class DDR(config: DDRConfig) extends Module {
  val io = IO(new Bundle {
    /** Memory port */
    val mem = Flipped(BurstReadWriteMemIO(config.addrWidth, config.dataWidth))
    /** DDR port */
    val ddr = DDRIO(config)
    /** Debug port */
    val debug = new Bundle {
      val burstCounter = Output(UInt())
    }
  })

  object State {
    val idle :: readWait :: writeWait :: Nil = Enum(3)
  }

  // Registers
  val stateReg = RegInit(State.idle)
  val burstLength = Util.latch(io.mem.burstLength, stateReg === State.idle, stateReg === State.idle)

  // Read/write control signals
  val read = stateReg === State.idle && io.mem.rd && !io.ddr.waitReq
  val write = stateReg === State.idle && io.mem.wr && !io.ddr.waitReq

  // Burst counter enable flag
  val burstCounterEnable =
    write ||
    (stateReg === State.writeWait && !io.ddr.waitReq) ||
    (stateReg === State.readWait && io.ddr.valid)

  // Burst counter
  val (burstCounter, burstCounterDone) = Counter.dynamic(burstLength, burstCounterEnable)

  // FSM
  stateReg := MuxCase(stateReg, Seq(
    burstCounterDone -> State.idle,
    read -> State.readWait,
    write -> State.writeWait
  ))

  // Outputs
  io.mem.burstDone := burstCounterDone
  io.mem.waitReq := io.ddr.waitReq
  io.mem.valid := io.ddr.valid
  io.ddr.burstLength := burstLength
  io.ddr.rd := io.mem.rd && stateReg =/= State.readWait
  io.ddr.wr := io.mem.wr || stateReg === State.writeWait
  io.ddr.addr := io.mem.addr
  io.ddr.mask := io.mem.mask
  io.ddr.din := io.mem.din
  io.mem.dout := io.ddr.dout
  io.debug.burstCounter := burstCounter

  printf(p"DDR(state: $stateReg, counter: $burstCounter ($burstCounterDone)\n")
}
