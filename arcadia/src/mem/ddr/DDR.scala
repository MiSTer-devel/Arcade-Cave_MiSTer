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

package arcadia.mem.ddr

import arcadia.mem.BurstReadWriteMemIO
import arcadia.util.Counter
import chisel3._
import chisel3.util._

/**
 * Handles reading/writing data to a DDR memory device.
 *
 * @param config The DDR configuration.
 */
class DDR(config: Config) extends Module {
  val io = IO(new Bundle {
    /** Memory port */
    val mem = Flipped(BurstReadWriteMemIO(config))
    /** DDR port */
    val ddr = BurstReadWriteMemIO(config)
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
  val burstLength = Mux(stateReg === State.idle,
    io.mem.burstLength,
    RegEnable(io.mem.burstLength, stateReg === State.idle)
  )

  // Control signals
  val read = stateReg === State.idle && io.mem.rd
  val write = (stateReg === State.idle || stateReg === State.writeWait) && io.mem.wr
  val effectiveRead = read && !io.ddr.waitReq
  val effectiveWrite = write && !io.ddr.waitReq
  val burstCounterEnable = (stateReg === State.readWait && io.ddr.valid) || effectiveWrite

  // Burst counter
  val (burstCounter, burstCounterWrap) = Counter.dynamic(burstLength, burstCounterEnable)

  // FSM
  stateReg := MuxCase(stateReg, Seq(
    burstCounterWrap -> State.idle,
    effectiveRead -> State.readWait,
    effectiveWrite -> State.writeWait
  ))

  // Connect I/O ports
  io.mem <> io.ddr

  // Outputs
  io.mem.burstDone := burstCounterWrap
  io.ddr.rd := read
  io.ddr.wr := write
  io.ddr.burstLength := burstLength
  io.debug.burstCounter := burstCounter

  printf(p"DDR(state: $stateReg, counter: $burstCounter ($burstCounterWrap)\n")
}
