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

package cave

import axon.mem._
import axon.types._
import cave.types._
import chisel3._
import chisel3.util._
import axon.util.Counter

/**
 * A DDR memory arbiter.
 *
 * The DDR memory arbiter routes requests from multiple input ports to a single output port.
 */
class DDRArbiter extends Module {
  val io = IO(new Bundle {
    /** Download port */
    val download = DownloadIO()
    /** Program ROM port */
    val progRom = Flipped(BurstReadWriteMemIO(Config.DDR_ADDR_WIDTH, Config.DDR_DATA_WIDTH))
    /** Sound ROM port */
    val soundRom = Flipped(new CacheIO)
    /** Tile ROM port */
    val tileRom = Flipped(new TileRomIO)
    /** Frame buffer to DDR port */
    val fbToDDR = Flipped(BurstWriteMemIO(Config.DDR_ADDR_WIDTH, Config.DDR_DATA_WIDTH))
    /** Frame buffer from DDR port */
    val fbFromDDR = Flipped(BurstReadMemIO(Config.DDR_ADDR_WIDTH, Config.DDR_DATA_WIDTH))
    /** DDR port */
    val ddr = BurstReadWriteMemIO(Config.DDR_ADDR_WIDTH, Config.DDR_DATA_WIDTH)
    /** Debug port */
    val debug = new Bundle {
      val idle = Output(Bool())
      val check1 = Output(Bool())
      val check2 = Output(Bool())
      val check3 = Output(Bool())
      val check4 = Output(Bool())
      val progRomReq = Output(Bool())
      val progRomWait = Output(Bool())
      val soundRomReq = Output(Bool())
      val soundRomWait = Output(Bool())
      val tileRomReq = Output(Bool())
      val tileRomWait = Output(Bool())
      val fbToDDR = Output(Bool())
      val fbFromDDR = Output(Bool())
      val download = Output(Bool())
    }
  })

  // States
  object State {
    val idle :: check1 :: check2 :: check3 :: check4 :: progRomReq :: progRomWait :: soundRomReq :: soundRomWait :: tileRomReq :: tileRomWait :: fbFromDDR :: fbToDDR :: download :: Nil = Enum(14)
  }

  // Wires
  val nextState = Wire(UInt())

  // Registers
  val stateReg = RegNext(nextState, State.idle)
  val cacheDataReg = Reg(Vec(DDRArbiter.CACHE_BURST_LENGTH, Bits(DDRArbiter.DATA_WIDTH.W)))
  val progRomReqReg = RegInit(false.B)
  val progRomAddrReg = RegInit(0.U)
  val progRomBurstCountReg = RegInit(0.U(8.W))
  val soundRomReqReg = RegInit(false.B)
  val soundRomAddrReg = RegInit(0.U)
  val tileRomReqReg = RegInit(false.B)
  val tileRomAddrReg = RegInit(0.U)
  val tileRomBurstCountReg = RegInit(0.U(8.W))

  // Control signals
  val progRomBurstDone = stateReg === State.progRomWait && io.ddr.burstDone
  val soundRomBurstDone = stateReg === State.soundRomWait && io.ddr.burstDone
  val tileRomBurstDone = stateReg === State.tileRomWait && io.ddr.burstDone
  val fbFromDDRBurstDone = stateReg === State.fbFromDDR && io.ddr.burstDone
  val fbToDDRBurstDone = stateReg === State.fbToDDR && io.ddr.burstDone

  // Set download data and byte mask
  val downloadData = Cat(Seq.tabulate(4) { _ => io.download.dout })
  val downloadMask = 3.U << io.download.addr(2, 0)

  // Counters
  val (rrCounterValue, _) = Counter.static(4, enable = stateReg === State.idle && nextState =/= State.idle)

  // Shift the DDR output data into the cache data register
  when(io.ddr.valid) { cacheDataReg := cacheDataReg.tail :+ io.ddr.dout }

  // Latch program ROM requests
  when(io.progRom.rd) {
    progRomReqReg := true.B
    progRomAddrReg := io.progRom.addr
    progRomBurstCountReg := io.progRom.burstLength
  }.elsewhen(progRomBurstDone) {
    progRomReqReg := false.B
  }

  // Latch sound ROM requests
  when(io.soundRom.rd) {
    soundRomReqReg := true.B
    soundRomAddrReg := io.soundRom.addr
  }.elsewhen(soundRomBurstDone) {
    soundRomReqReg := false.B
  }

  // Latch graphics requests
  when(io.tileRom.rd) {
    tileRomReqReg := true.B
    tileRomAddrReg := io.tileRom.addr
    tileRomBurstCountReg := io.tileRom.burstLength
  }.elsewhen(tileRomBurstDone) {
    tileRomReqReg := false.B
  }

  // Default to the previous state
  nextState := stateReg

  // FSM
  switch(stateReg) {
    is(State.idle) {
      nextState := MuxLookup(rrCounterValue, State.check4, Seq(
        0.U -> State.check1,
        1.U -> State.check2,
        2.U -> State.check3
      ))
    }

    is(State.check1) {
      nextState := MuxCase(stateReg, Seq(
        io.fbFromDDR.rd -> State.fbFromDDR,
        io.download.cs -> State.download,
        progRomReqReg -> State.progRomReq,
        soundRomReqReg -> State.soundRomReq,
        tileRomReqReg -> State.tileRomReq,
        io.fbToDDR.wr -> State.fbToDDR
      ))
    }

    is(State.check2) {
      nextState := MuxCase(stateReg, Seq(
        io.fbFromDDR.rd -> State.fbFromDDR,
        io.download.cs -> State.download,
        soundRomReqReg -> State.soundRomReq,
        tileRomReqReg -> State.tileRomReq,
        io.fbToDDR.wr -> State.fbToDDR,
        progRomReqReg -> State.progRomReq
      ))
    }

    is(State.check3) {
      nextState := MuxCase(stateReg, Seq(
        io.fbFromDDR.rd -> State.fbFromDDR,
        io.download.cs -> State.download,
        tileRomReqReg -> State.tileRomReq,
        io.fbToDDR.wr -> State.fbToDDR,
        progRomReqReg -> State.progRomReq,
        soundRomReqReg -> State.soundRomReq
      ))
    }

    is(State.check4) {
      nextState := MuxCase(stateReg, Seq(
        io.fbFromDDR.rd -> State.fbFromDDR,
        io.download.cs -> State.download,
        io.fbToDDR.wr -> State.fbToDDR,
        progRomReqReg -> State.progRomReq,
        soundRomReqReg -> State.soundRomReq,
        tileRomReqReg -> State.tileRomReq
      ))
    }

    is(State.progRomReq) {
      when(!io.ddr.waitReq) { nextState := State.progRomWait }
    }

    is(State.progRomWait) {
      when(io.ddr.burstDone) { nextState := State.idle }
    }

    is(State.soundRomReq) {
      when(!io.ddr.waitReq) { nextState := State.soundRomWait }
    }

    is(State.soundRomWait) {
      when(io.ddr.burstDone) { nextState := State.idle }
    }

    is(State.tileRomReq) {
      when(!io.ddr.waitReq) { nextState := State.tileRomWait }
    }

    is(State.tileRomWait) {
      when(io.ddr.burstDone) { nextState := State.idle }
    }

    is(State.fbFromDDR) {
      when(io.ddr.burstDone) { nextState := State.idle }
    }

    is(State.fbToDDR) {
      when(io.ddr.burstDone) { nextState := State.idle }
    }

    is(State.download) {
      when(!io.download.cs) { nextState := State.idle }
    }
  }

  // Outputs
  io.ddr.rd := MuxLookup(stateReg, false.B, Seq(
    State.progRomReq -> true.B,
    State.soundRomReq -> true.B,
    State.tileRomReq -> true.B,
    State.fbFromDDR -> io.fbFromDDR.rd
  ))
  io.ddr.wr := MuxLookup(stateReg, false.B, Seq(
    State.fbToDDR -> io.fbToDDR.wr,
    State.download -> io.download.wr
  ))
  io.ddr.addr := MuxLookup(stateReg, 0.U, Seq(
    State.progRomReq -> progRomAddrReg,
    State.soundRomReq -> soundRomAddrReg,
    State.tileRomReq -> tileRomAddrReg,
    State.fbFromDDR -> io.fbFromDDR.addr,
    State.fbToDDR -> io.fbToDDR.addr,
    State.download -> io.download.addr
  ))
  io.ddr.burstLength := MuxLookup(stateReg, 1.U, Seq(
    State.progRomReq -> progRomBurstCountReg,
    State.soundRomReq -> DDRArbiter.CACHE_BURST_LENGTH.U,
    State.tileRomReq -> tileRomBurstCountReg,
    State.fbFromDDR -> io.fbFromDDR.burstLength,
    State.fbToDDR -> io.fbToDDR.burstLength
  ))
  io.ddr.mask := MuxLookup(stateReg, 0.U, Seq(
    State.fbToDDR -> io.fbToDDR.mask,
    State.download -> downloadMask
  ))
  io.ddr.din := MuxLookup(stateReg, 0.U, Seq(
    State.fbToDDR -> io.fbToDDR.din,
    State.download -> downloadData
  ))
  io.progRom.waitReq := Mux(stateReg === State.progRomWait, io.ddr.waitReq, false.B)
  io.progRom.valid := Mux(stateReg === State.progRomWait, io.ddr.valid, false.B)
  io.progRom.burstDone := progRomBurstDone
  io.progRom.dout := io.ddr.dout
  io.soundRom.valid := RegNext(soundRomBurstDone, false.B)
  io.soundRom.dout := cacheDataReg.asUInt
  io.tileRom.waitReq := Mux(stateReg === State.tileRomWait, io.ddr.waitReq, true.B)
  io.tileRom.valid := Mux(stateReg === State.tileRomWait, io.ddr.valid, false.B)
  io.tileRom.burstDone := tileRomBurstDone
  io.tileRom.dout := io.ddr.dout
  io.fbFromDDR.waitReq := Mux(stateReg === State.fbFromDDR, io.ddr.waitReq, true.B)
  io.fbFromDDR.valid := Mux(stateReg === State.fbFromDDR, io.ddr.valid, false.B)
  io.fbFromDDR.burstDone := fbFromDDRBurstDone
  io.fbFromDDR.dout := io.ddr.dout
  io.fbToDDR.waitReq := Mux(stateReg === State.fbToDDR, io.ddr.waitReq, true.B)
  io.fbToDDR.burstDone := fbToDDRBurstDone
  io.download.waitReq := Mux(stateReg === State.download, io.download.wr && io.ddr.waitReq, io.download.wr)
  io.debug.idle := stateReg === State.idle
  io.debug.check1 := stateReg === State.check1
  io.debug.check2 := stateReg === State.check2
  io.debug.check3 := stateReg === State.check3
  io.debug.check4 := stateReg === State.check4
  io.debug.progRomReq := stateReg === State.progRomReq
  io.debug.progRomWait := stateReg === State.progRomWait
  io.debug.soundRomReq := stateReg === State.soundRomReq
  io.debug.soundRomWait := stateReg === State.soundRomWait
  io.debug.tileRomReq := stateReg === State.tileRomReq
  io.debug.tileRomWait := stateReg === State.tileRomWait
  io.debug.fbFromDDR := stateReg === State.fbFromDDR
  io.debug.fbToDDR := stateReg === State.fbToDDR
  io.debug.download := stateReg === State.download

  printf(p"DDRArbiter(state: $stateReg, nextState: $nextState, burstDone: ${io.ddr.burstDone} download: 0x${Hexadecimal(downloadData)} (0x${Hexadecimal(downloadMask)})\n")
}

object DDRArbiter {
  /** The width of the address bus */
  val ADDR_WIDTH = 32
  /** The width of the data bus */
  val DATA_WIDTH = 64
  /** The length of the cache burst */
  val CACHE_BURST_LENGTH = 4
}
