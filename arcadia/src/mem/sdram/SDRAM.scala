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

import arcadia.mem.BurstMemIO
import arcadia.mem.request.ReadWriteRequest
import chisel3._
import chisel3.util._

/**
 * Handles reading/writing data to a SDRAM memory device.
 *
 * @param config The SDRAM configuration.
 */
class SDRAM(config: Config) extends Module {
  // Sanity check
  assert(Seq(1, 2, 4, 8).contains(config.burstLength), "SDRAM burst length must be 1, 2, 4, or 8")

  val io = IO(new Bundle {
    /** Memory port */
    val mem = Flipped(BurstMemIO(config))
    /** Control port */
    val sdram = SDRAMIO(config)
    /** Debug port */
    val debug = Output(new Bundle {
      val init = Bool()
      val mode = Bool()
      val idle = Bool()
      val active = Bool()
      val read = Bool()
      val write = Bool()
      val refresh = Bool()
    })
  })

  // States
  object State {
    val init :: mode :: idle :: active :: read :: write :: refresh :: Nil = Enum(7)
  }

  // Commands
  object Command {
    val mode :: refresh :: precharge :: active :: write :: read :: stop :: nop :: deselect :: Nil = Enum(9)
  }

  // State register
  val nextState = Wire(UInt())
  val stateReg = RegNext(nextState, State.init)

  // Command register
  val nextCommand = Wire(UInt())
  val commandReg = RegNext(nextCommand, Command.nop)

  // Assert the latch signal when a request should be latched
  val latch = stateReg =/= State.active && nextState === State.active

  // Asserted when there is a read or write request
  val isReadWrite = io.mem.rd || io.mem.wr

  // Request register
  val request = ReadWriteRequest(io.mem.rd, io.mem.wr, Address.fromByteAddress(config, io.mem.addr), 0.U, 0.U)
  val requestReg = RegEnable(request, latch)

  // SDRAM registers
  //
  // Using simple registers for SDRAM I/O allows better timings to be achieved, because they can be
  // optimized and moved physically closer to the FPGA pins during routing (i.e. fast registers).
  val bankReg = Reg(UInt())
  val addrReg = Reg(UInt())
  val dinReg = RegNext(io.mem.din)
  val doutReg = RegNext(io.sdram.dout)

  // Counters
  val (waitCounter, _) = Counter(0 until config.waitCounterMax, reset = nextState =/= stateReg)
  val (refreshCounter, _) = Counter(0 until config.refreshCounterMax,
    enable = stateReg =/= State.init && stateReg =/= State.mode,
    reset = stateReg === State.refresh && waitCounter === 0.U
  )

  // Control signals
  val modeDone = waitCounter === (config.modeWait - 1).U
  val activeDone = waitCounter === (config.activeWait - 1).U
  val readDone = waitCounter === (config.readWait - 1).U
  val writeDone = waitCounter === (config.writeWait - 1).U
  val refreshDone = waitCounter === (config.refreshWait - 1).U
  val triggerRefresh = refreshCounter >= (config.refreshInterval - 1).U
  val burstBusy = waitCounter < (config.burstLength - 1).U
  val burstDone = waitCounter === (config.burstLength - 1).U

  // Deassert the wait signal at the start of a read request, or during a write request
  val waitReq = {
    val idle = stateReg === State.idle && !isReadWrite
    val read = latch && request.rd
    val write = (stateReg === State.active && activeDone && requestReg.wr) || (stateReg === State.write && burstBusy)
    !(idle || read || write)
  }

  // Assert the valid signal after the first word has been bursted during a read
  val valid = RegNext(stateReg === State.read && waitCounter > (config.casLatency - 1).U, false.B)

  // Assert the burst done signal when a read/write burst has completed
  val memBurstDone = {
    val readBurstDone = stateReg === State.read && readDone
    val writeBurstDone = stateReg === State.write && burstDone
    RegNext(readBurstDone, false.B) || writeBurstDone
  }

  // Default to the previous state
  nextState := stateReg

  // Default to a NOP
  nextCommand := Command.nop

  def mode() = {
    nextCommand := Command.mode
    nextState := State.mode
    addrReg := config.mode
  }

  def idle() = {
    nextState := State.idle
  }

  def active() = {
    nextCommand := Command.active
    nextState := State.active
    bankReg := request.addr.bank
    addrReg := request.addr.row
  }

  def read() = {
    nextCommand := Command.read
    nextState := State.read
    bankReg := requestReg.addr.bank
    addrReg := "b001".U ## requestReg.addr.col.pad(10)
  }

  def write() = {
    nextCommand := Command.write
    nextState := State.write
    bankReg := requestReg.addr.bank
    addrReg := "b001".U ## requestReg.addr.col.pad(10)
  }

  def refresh() = {
    nextCommand := Command.refresh
    nextState := State.refresh
  }

  // FSM
  switch(stateReg) {
    // Execute initialization sequence
    is(State.init) {
      addrReg := "b0010000000000".U
      when(waitCounter === 0.U) {
        nextCommand := Command.deselect
      }.elsewhen(waitCounter === (config.deselectWait - 1).U) {
        nextCommand := Command.precharge
      }.elsewhen(waitCounter === (config.deselectWait + config.prechargeWait - 1).U) {
        nextCommand := Command.refresh
      }.elsewhen(waitCounter === (config.deselectWait + config.prechargeWait + config.refreshWait - 1).U) {
        nextCommand := Command.refresh
      }.elsewhen(waitCounter === (config.deselectWait + config.prechargeWait + config.refreshWait + config.refreshWait - 1).U) {
        mode()
      }
    }

    // Set mode register
    is(State.mode) {
      when(modeDone) { idle() }
    }

    // Wait for request
    is(State.idle) {
      when(triggerRefresh) { refresh() }.elsewhen(isReadWrite) { active() }
    }

    // Activate row
    is(State.active) {
      when(activeDone) {
        when(requestReg.wr) { write() }.otherwise { read() }
      }
    }

    // Execute read command
    is(State.read) {
      when(readDone) {
        when(triggerRefresh) { refresh() }.elsewhen(isReadWrite) { active() }.otherwise { idle() }
      }
    }

    // Execute write command
    is(State.write) {
      when(writeDone) {
        when(triggerRefresh) { refresh() }.elsewhen(isReadWrite) { active() }.otherwise { idle() }
      }
    }

    // Execute refresh command
    is(State.refresh) {
      when(refreshDone) {
        when(isReadWrite) { active() }.otherwise { idle() }
      }
    }
  }

  // Outputs
  io.mem.waitReq := waitReq
  io.mem.valid := valid
  io.mem.burstDone := memBurstDone
  io.mem.dout := doutReg
  io.sdram.cke := true.B
  io.sdram.cs_n := commandReg(3)
  io.sdram.ras_n := commandReg(2)
  io.sdram.cas_n := commandReg(1)
  io.sdram.we_n := commandReg(0)
  io.sdram.oe := stateReg === State.write
  io.sdram.bank := bankReg
  io.sdram.addr := addrReg
  io.sdram.din := dinReg
  io.debug.init := stateReg === State.init
  io.debug.mode := stateReg === State.mode
  io.debug.idle := stateReg === State.idle
  io.debug.active := stateReg === State.active
  io.debug.read := stateReg === State.read
  io.debug.write := stateReg === State.write
  io.debug.refresh := stateReg === State.refresh

  // Debug
  if (sys.env.get("DEBUG").contains("1")) {
    printf(p"SDRAM(state: $stateReg, nextState: $nextState, command: $commandReg, nextCommand: $nextCommand, bank: $bankReg, addr: $addrReg, waitCounter: $waitCounter, wait: $waitReq, valid: $valid, burstDone: $memBurstDone)\n")
  }
}
