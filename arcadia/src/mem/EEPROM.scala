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

package arcadia.mem

import arcadia.Util
import chisel3._
import chisel3.util._

/** Presents an asynchronous memory interface as a serial EEPROM device. */
class EEPROM extends Module {
  val io = IO(new Bundle {
    /** Memory port */
    val mem = AsyncMemIO(EEPROM.ADDR_WIDTH + 1, EEPROM.DATA_WIDTH)
    /** Serial port */
    val serial = new SerialIO
    /** Debug port */
    val debug = Output(new Bundle {
      val idle = Bool()
      val command = Bool()
      val read = Bool()
      val readWait = Bool()
      val write = Bool()
      val shiftIn = Bool()
      val shiftOut = Bool()
    })
  })

  // States
  object State {
    val idle :: command :: read :: readWait :: write :: shiftIn :: shiftOut :: Nil = Enum(7)
  }

  // State register
  val stateReg = RegInit(State.idle)

  // Serial clock
  val serialClock = io.serial.cs && Util.rising(io.serial.sck)

  // Registers
  val counterReg = RegInit(0.U(17.W))
  val addrReg = Reg(UInt(EEPROM.ADDR_WIDTH.W))
  val dataReg = Reg(UInt(EEPROM.DATA_WIDTH.W))
  val opcodeReg = Reg(UInt(EEPROM.OPCODE_WIDTH.W))
  val serialReg = RegInit(true.B)
  val writeAllReg = RegInit(false.B)
  val writeEnableReg = RegInit(false.B)

  // Control signals
  val done = counterReg(0)
  val read = opcodeReg === 2.U
  val write = opcodeReg === 1.U && writeEnableReg
  val erase = opcodeReg === 3.U && writeEnableReg
  val writeAll = opcodeReg === 0.U && addrReg(4, 3) === 1.U && writeEnableReg
  val eraseAll = opcodeReg === 0.U && addrReg(4, 3) === 2.U && writeEnableReg
  val enableWrite = opcodeReg === 0.U && addrReg(4, 3) === 3.U
  val disableWrite = opcodeReg === 0.U && addrReg(4, 3) === 0.U

  // Update the counter shift register
  when((stateReg === State.command || stateReg === State.shiftIn || stateReg === State.shiftOut) && serialClock) {
    counterReg := counterReg >> 1
  }

  // Default to the previous state
  stateReg := stateReg

  // FSM
  switch(stateReg) {
    // Wait for start bit
    is(State.idle) {
      counterReg := 0x80.U
      addrReg := 0.U
      dataReg := 0xffff.U
      serialReg := true.B // ready
      writeAllReg := false.B
      when(serialClock && io.serial.sdi) { stateReg := State.command }
    }

    // Wait for opcode/address
    is(State.command) {
      when(serialClock) {
        val data = opcodeReg ## addrReg ## io.serial.sdi

        // Update opcode register
        opcodeReg := data(6, 5)

        // Update address register
        addrReg := data(5, 0)

        when(done) {
          when(read) {
            counterReg := 0x10000.U
            serialReg := false.B // dummy bit
            stateReg := State.read
          }.elsewhen(write) {
            counterReg := 0x8000.U
            stateReg := State.shiftIn
          }.elsewhen(erase) {
            stateReg := State.write
          }.elsewhen(writeAll) {
            counterReg := 0x8000.U
            addrReg := 0.U
            writeAllReg := true.B
            stateReg := State.shiftIn
          }.elsewhen(eraseAll) {
            addrReg := 0.U
            writeAllReg := true.B
            stateReg := State.write
          }.elsewhen(enableWrite) {
            writeEnableReg := true.B
            stateReg := State.idle
          }.elsewhen(disableWrite) {
            writeEnableReg := false.B
            stateReg := State.idle
          }.otherwise {
            stateReg := State.idle
          }
        }
      }
    }

    // Read data from memory
    is(State.read) {
      when(io.mem.valid) {
        dataReg := io.mem.dout
        stateReg := State.shiftOut
      }.elsewhen(!io.mem.waitReq) {
        stateReg := State.readWait
      }
    }

    // Wait for valid data
    is(State.readWait) {
      when(io.mem.valid) {
        dataReg := io.mem.dout
        stateReg := State.shiftOut
      }
    }

    // Write data to memory
    is(State.write) {
      when(!io.mem.waitReq) {
        addrReg := addrReg + 1.U
        when(!writeAllReg || addrReg.andR) { stateReg := State.idle }
      }
    }

    // Shift serial data in
    is(State.shiftIn) {
      when(serialClock) {
        serialReg := false.B // busy
        dataReg := dataReg ## io.serial.sdi
        when(done) { stateReg := State.write }
      }
    }

    // Shift serial data out
    is(State.shiftOut) {
      when(serialClock) {
        dataReg := dataReg ## 0.U
        serialReg := dataReg.head(1)
        when(done) { stateReg := State.idle }
      }
    }
  }

  // Return to idle state when chip select is deasserted
  when(!io.serial.cs) { stateReg := State.idle }

  // Outputs
  io.serial.sdo := serialReg
  io.mem.rd := stateReg === State.read
  io.mem.wr := stateReg === State.write
  io.mem.addr := addrReg ## 0.U // convert to byte addressing
  io.mem.mask := 2.U
  io.mem.din := dataReg
  io.debug.idle := stateReg === State.idle
  io.debug.command := stateReg === State.command
  io.debug.read := stateReg === State.read
  io.debug.readWait := stateReg === State.readWait
  io.debug.write := stateReg === State.write
  io.debug.shiftIn := stateReg === State.shiftIn
  io.debug.shiftOut := stateReg === State.shiftOut

  // Debug
  if (sys.env.get("DEBUG").contains("1")) {
    printf(p"EEPROM(state: $stateReg, bitCounter: ${Binary(counterReg)}, addr: 0x${Hexadecimal(addrReg)}, opcode: ${Hexadecimal(opcodeReg)}, data: ${Hexadecimal(dataReg)}, done: $done)\n")
  }
}

object EEPROM {
  /** The width of the EEPROM opcode */
  val OPCODE_WIDTH = 2
  /** The width of the EEPROM address bus */
  val ADDR_WIDTH = 6
  /** The width of the EEPROM data bus */
  val DATA_WIDTH = 16
  /** The number of words in the EEPROM */
  val DEPTH = 64
}
