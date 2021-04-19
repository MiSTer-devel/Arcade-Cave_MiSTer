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
 * Copyright (c) 2021 Josh Bassett
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

package axon.mem

import chisel3._
import chisel3.util._

/**
 * An interface for reading and writing to SDRAM.
 *
 * @param config The SDRAM configuration.
 */
class SDRAMIO private(config: SDRAMConfig) extends Bundle {
  /** Clock enable */
  val cke = Output(Bool())
  /** Chip select (active-low) */
  val cs_n = Output(Bool())
  /** Row address strobe (active-low) */
  val ras_n = Output(Bool())
  /** Column address strobe (active-low) */
  val cas_n = Output(Bool())
  /** Write enable (active-low) */
  val we_n = Output(Bool())
  /** Output enable */
  val oe = Output(Bool())
  /** Bank bus */
  val bank = Output(UInt(config.bankWidth.W))
  /** Address bus */
  val addr = Output(UInt(config.rowWidth.W))
  /** Data input bus */
  val din = Output(Bits(config.dataWidth.W))
  /** Data output bus */
  val dout = Input(Bits(config.dataWidth.W))
}

object SDRAMIO {
  def apply(config: SDRAMConfig) = new SDRAMIO(config)
}

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
 * @param casLatency     The delay in clock cycles, between the start of a read command and the
 *                       availability of the output data.
 * @param writeBurstMode The write burst mode (0=burst, 1=single).
 * @param tINIT          The initialization delay (ns).
 * @param tMRD           The mode register cycle time (ns).
 * @param tRC            The row cycle time (ns).
 * @param tRCD           The RAS to CAS delay (ns).
 * @param tRP            The precharge to activate delay (ns).
 * @param tWR            The write recovery time (ns).
 * @param tREFI          The refresh interval (ns).
 */
case class SDRAMConfig(clockFreq: Double,
                       bankWidth: Int = 2,
                       rowWidth: Int = 13,
                       colWidth: Int = 9,
                       dataWidth: Int = 16,
                       burstLength: Int = 1,
                       burstType: Int = 0,
                       casLatency: Int = 2,
                       writeBurstMode: Int = 0,
                       tINIT: Double = 200000,
                       tMRD: Double = 12,
                       tRC: Double = 60,
                       tRCD: Double = 18,
                       tRP: Double = 18,
                       tWR: Double = 12,
                       tREFI: Double = 7800) {
  /** The width of the address bus (i.e. the byte width of all banks, rows, and columns). */
  val addrWidth = bankWidth + rowWidth + colWidth + 1
  /** The SDRAM clock period (ns). */
  val clockPeriod = 1 / clockFreq * 1000000000
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

  /** The mode opcode value used for configuring the SDRAM module. */
  def mode: UInt =
    0.U(3.W) ## // unused
    writeBurstMode.U(1.W) ##
    0.U(2.W) ## // unused
    casLatency.U(3.W) ##
    burstType.U(1.W) ##
    log2Ceil(burstLength).U(3.W)
}

/** Represents the address of a word stored in SDRAM. */
class SDRAMAddress(private val config: SDRAMConfig) extends Bundle {
  /** The bank address */
  val bank = UInt(config.bankWidth.W)
  /** The row address */
  val row = UInt(config.rowWidth.W)
  /** The column address */
  val col = UInt(config.colWidth.W)
}

object SDRAMAddress {
  /**
   * Converts a byte address to a SDRAM address.
   *
   * @param addr   The address.
   * @param config The SDRAM configuration.
   */
  def fromByteAddress(addr: UInt)(config: SDRAMConfig) = {
    val n = log2Ceil(config.dataWidth / 8)
    (addr >> n).asTypeOf(new SDRAMAddress(config))
  }
}

/**
 * Handles reading/writing data to a SDRAM memory device.
 *
 * @param config The SDRAM configuration.
 */
class SDRAM(val config: SDRAMConfig) extends Module {
  // Sanity check
  assert(Seq(1, 2, 4, 8).contains(config.burstLength), "SDRAM burst length must be 1, 2, 4, or 8")

  val io = IO(new Bundle {
    /** Memory port */
    val mem = Flipped(BurstReadWriteMemIO(config.addrWidth, config.dataWidth))
    /** SDRAM port */
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

  // Request register
  val request = MemRequest(io.mem.rd, io.mem.wr, SDRAMAddress.fromByteAddress(io.mem.addr)(config))
  val requestReg = RegEnable(request, latch)

  // Bank register
  val bankReg = RegNext(Mux1H(Seq(
    (nextState === State.active) -> request.addr.bank,
    (nextState === State.read) -> requestReg.addr.bank,
    (nextState === State.write) -> requestReg.addr.bank
  )), 0.U)

  // Address register
  val addrReg = RegNext(Mux1H(Seq(
    (nextState === State.init) -> "b0010000000000".U,
    (nextState === State.mode) -> config.mode,
    (nextState === State.active) -> request.addr.row,
    (nextState === State.read) -> "b0010".U ## requestReg.addr.col,
    (nextState === State.write) -> "b0010".U ## requestReg.addr.col
  )), 0.U)

  // Data I/O registers
  //
  // Using simple registers for data I/O allows better timings to be achieved, because they can be
  // optimized and moved physically closer to the FPGA pins during routing (i.e. fast registers).
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
  val refresh = refreshCounter >= (config.refreshInterval - 1).U
  val burstBusy = waitCounter < (config.burstLength - 1).U
  val burstDone = waitCounter === (config.burstLength - 1).U

  // Deassert the wait signal at the start of a read request, or during a write request
  val waitReq = {
    val idle = stateReg === State.idle && !request.valid
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

  // FSM
  switch(stateReg) {
    // Execute initialization sequence
    is(State.init) {
      when(waitCounter === 0.U) {
        nextCommand := Command.deselect
      }.elsewhen(waitCounter === (config.deselectWait - 1).U) {
        nextCommand := Command.precharge
      }.elsewhen(waitCounter === (config.deselectWait + config.prechargeWait - 1).U) {
        nextCommand := Command.refresh
      }.elsewhen(waitCounter === (config.deselectWait + config.prechargeWait + config.refreshWait - 1).U) {
        nextCommand := Command.refresh
      }.elsewhen(waitCounter === (config.deselectWait + config.prechargeWait + config.refreshWait + config.refreshWait - 1).U) {
        nextCommand := Command.mode
        nextState := State.mode
      }
    }

    // Set mode register
    is(State.mode) {
      when(modeDone) { nextState := State.idle }
    }

    // Wait for request
    is(State.idle) {
      when(refresh) {
        nextCommand := Command.refresh
        nextState := State.refresh
      }.elsewhen(request.valid) {
        nextCommand := Command.active
        nextState := State.active
      }
    }

    // Activate row
    is(State.active) {
      when(activeDone) {
        when(requestReg.wr) {
          nextCommand := Command.write
          nextState := State.write
        }.otherwise {
          nextCommand := Command.read
          nextState := State.read
        }
      }
    }

    // Execute read command
    is(State.read) {
      when(readDone) {
        when(refresh) {
          nextCommand := Command.refresh
          nextState := State.refresh
        }.elsewhen(request.valid) {
          nextCommand := Command.active
          nextState := State.active
        }.otherwise {
          nextState := State.idle
        }
      }
    }

    // Execute write command
    is(State.write) {
      when(writeDone) {
        when(refresh) {
          nextCommand := Command.refresh
          nextState := State.refresh
        }.elsewhen(request.valid) {
          nextCommand := Command.active
          nextState := State.active
        }.otherwise {
          nextState := State.idle
        }
      }
    }

    // Execute refresh command
    is(State.refresh) {
      when(refreshDone) {
        when(request.valid) {
          nextCommand := Command.active
          nextState := State.active
        }.otherwise {
          nextState := State.idle
        }
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

  printf(p"SDRAM(state: $stateReg, nextState: $nextState, command: $commandReg, nextCommand: $nextCommand, waitCounter: $waitCounter, wait: $waitReq, valid: $valid, burstDone: $memBurstDone)\n")
}
