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
import axon.types.DownloadIO
import cave.types.{CacheIO, TileRomIO}
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
    /** DDR port */
    val ddr = new BurstReadWriteMemIO(DDRArbiter.ADDR_WIDTH, DDRArbiter.DATA_WIDTH)
    /** Cache port */
    val cache = Flipped(new CacheIO)
    /** Graphics port */
    val gfx = Flipped(new TileRomIO)
    /** Frame buffer to DDR port */
    val fbToDDR = Flipped(BurstWriteMemIO(DDRArbiter.ADDR_WIDTH, DDRArbiter.DATA_WIDTH))
    /** Frame buffer from DDR port */
    val fbFromDDR = Flipped(BurstReadMemIO(DDRArbiter.ADDR_WIDTH, DDRArbiter.DATA_WIDTH))
    /** Download port */
    val download = DownloadIO()
    /** Debug port */
    val debug = new Bundle {
      val idle = Output(Bool())
      val check1 = Output(Bool())
      val check2 = Output(Bool())
      val check3 = Output(Bool())
      val check4 = Output(Bool())
      val cacheReq = Output(Bool())
      val gfxReq = Output(Bool())
      val cacheWait = Output(Bool())
      val gfxWait = Output(Bool())
      val fbToDDR = Output(Bool())
      val fbFromDDR = Output(Bool())
      val download = Output(Bool())
    }
  })

  // States
  val idleState :: check1State :: check2State :: check3State :: check4State :: cacheReqState :: gfxReqState :: cacheWaitState :: gfxWaitState :: fbFromDDRState :: fbToDDRState :: downloadState :: Nil = Enum(12)

  // Wires
  val nextState = Wire(UInt())

  // Registers
  val stateReg = RegNext(nextState, idleState)
  val cacheReqReg = RegInit(false.B)
  val cacheAddrReg = RegInit(0.U)
  val cacheDataReg = Reg(Vec(DDRArbiter.CACHE_BURST_LENGTH, Bits(DDRArbiter.DATA_WIDTH.W)))
  val gfxReqReg = RegInit(false.B)
  val gfxAddrReg = RegInit(0.U)
  val gfxTinyBurstReg = RegInit(false.B)

  // Set download data and mask
  val downloadData = Cat(Seq.tabulate(8) { _ => io.download.dout })
  val downloadMask = 1.U << io.download.addr(2, 0)

  // Set the graphics burst length
  val gfxBurstLength = Mux(gfxTinyBurstReg, 8.U, 16.U)

  // Counters
  val (rrCounterValue, _) = Counter.static(4, enable = stateReg === idleState && nextState =/= idleState)
  val (cacheBurstValue, cacheBurstDone) = Counter.static(DDRArbiter.CACHE_BURST_LENGTH,
    enable = stateReg === cacheWaitState && io.ddr.valid,
    reset = stateReg === cacheReqState
  )
  val (gfxBurstValue, gfxBurstDone) = Counter.dynamic(gfxBurstLength,
    enable = stateReg === gfxWaitState && io.ddr.valid,
    reset = stateReg === gfxReqState
  )
  val (fbFromDDRBurstValue, fbFromDDRBurstDone) = Counter.dynamic(io.fbFromDDR.burstCount,
    enable = stateReg === fbFromDDRState && io.ddr.valid,
    reset = stateReg =/= fbFromDDRState
  )
  val (fbToDDRBurstValue, fbToDDRBurstDone) = Counter.dynamic(io.fbToDDR.burstCount,
    enable = stateReg === fbToDDRState && io.fbToDDR.wr && !io.ddr.waitReq,
    reset = stateReg =/= fbToDDRState
  )

  // Shift the DDR output data into the data register
  when(io.ddr.valid) { cacheDataReg := cacheDataReg.tail :+ io.ddr.dout }

  // Latch cache requests
  when(io.cache.rd) {
    cacheReqReg := true.B
    cacheAddrReg := io.cache.addr
  }.elsewhen(cacheBurstDone) {
    cacheReqReg := false.B
  }

  // Latch graphics requests
  when(io.gfx.rd) {
    gfxReqReg := true.B
    gfxAddrReg := io.gfx.addr
    gfxTinyBurstReg := io.gfx.tinyBurst
  }.elsewhen(gfxBurstDone) {
    gfxReqReg := false.B
  }

  // Default to the previous state
  nextState := stateReg

  // FSM
  switch(stateReg) {
    is(idleState) {
      nextState := MuxLookup(rrCounterValue, check4State, Seq(
        0.U -> check1State,
        1.U -> check2State,
        2.U -> check3State
      ))
    }

    is(check1State) {
      nextState := MuxCase(stateReg, Seq(
        io.fbFromDDR.rd -> fbFromDDRState,
        io.download.enable -> downloadState,
        cacheReqReg -> cacheReqState,
        gfxReqReg -> gfxReqState,
        io.fbToDDR.wr -> fbToDDRState
      ))
    }

    is(check2State) {
      nextState := MuxCase(stateReg, Seq(
        io.fbFromDDR.rd -> fbFromDDRState,
        io.download.enable -> downloadState,
        gfxReqReg -> gfxReqState,
        io.fbToDDR.wr -> fbToDDRState,
        cacheReqReg -> cacheReqState
      ))
    }

    is(check3State) {
      nextState := MuxCase(stateReg, Seq(
        io.fbFromDDR.rd -> fbFromDDRState,
        io.download.enable -> downloadState,
        io.fbToDDR.wr -> fbToDDRState,
        gfxReqReg -> gfxReqState,
        cacheReqReg -> cacheReqState
      ))
    }

    is(check4State) {
      nextState := MuxCase(stateReg, Seq(
        io.fbFromDDR.rd -> fbFromDDRState,
        io.download.enable -> downloadState,
        gfxReqReg -> gfxReqState,
        cacheReqReg -> cacheReqState,
        io.fbToDDR.wr -> fbToDDRState
      ))
    }

    is(cacheReqState) {
      when(!io.ddr.waitReq) { nextState := cacheWaitState }
    }

    is(cacheWaitState) {
      when(cacheBurstDone) { nextState := idleState }
    }

    is(gfxReqState) {
      when(!io.ddr.waitReq) { nextState := gfxWaitState }
    }

    is(gfxWaitState) {
      when(gfxBurstDone) { nextState := idleState }
    }

    is(fbFromDDRState) {
      when(fbFromDDRBurstDone) { nextState := idleState }
    }

    is(fbToDDRState) {
      when(fbToDDRBurstDone) { nextState := idleState }
    }

    is(downloadState) {
      when(!io.download.enable) { nextState := idleState }
    }
  }

  // Outputs
  io.ddr.rd := MuxLookup(stateReg, false.B, Seq(
    cacheReqState -> true.B,
    gfxReqState -> true.B,
    fbFromDDRState -> io.fbFromDDR.rd
  ))
  io.ddr.wr := MuxLookup(stateReg, false.B, Seq(
    fbToDDRState -> io.fbToDDR.wr,
    downloadState -> io.download.wr
  ))
  io.ddr.addr := MuxLookup(stateReg, 0.U, Seq(
    cacheReqState -> cacheAddrReg,
    gfxReqState -> gfxAddrReg,
    fbFromDDRState -> io.fbFromDDR.addr,
    fbToDDRState -> io.fbToDDR.addr,
    downloadState -> io.download.addr
  ))
  io.ddr.burstCount := MuxLookup(stateReg, 1.U, Seq(
    cacheReqState -> DDRArbiter.CACHE_BURST_LENGTH.U,
    gfxReqState -> gfxBurstLength,
    fbFromDDRState -> io.fbFromDDR.burstCount,
    fbToDDRState -> io.fbToDDR.burstCount
  ))
  io.ddr.mask := Mux(stateReg === downloadState, downloadMask, 0xff.U)
  io.ddr.din := MuxLookup(stateReg, 0.U, Seq(
    fbToDDRState -> io.fbToDDR.din,
    downloadState -> downloadData
  ))
  io.cache.valid := RegNext(cacheBurstDone, false.B)
  io.cache.dout := cacheDataReg.asUInt
  io.gfx.burstDone := RegNext(gfxBurstDone, false.B)
  io.gfx.valid := Mux(stateReg === gfxWaitState, io.ddr.valid, false.B)
  io.gfx.dout := io.ddr.dout
  io.fbFromDDR.waitReq := Mux(stateReg === fbFromDDRState, io.ddr.waitReq, true.B)
  io.fbFromDDR.valid := Mux(stateReg === fbFromDDRState, io.ddr.valid, false.B)
  io.fbFromDDR.dout := io.ddr.dout
  io.fbToDDR.waitReq := Mux(stateReg === fbToDDRState, io.ddr.waitReq, true.B)
  io.download.waitReq := Mux(stateReg === downloadState, io.download.wr && io.ddr.waitReq, io.download.wr)
  io.debug.idle := stateReg === idleState
  io.debug.check1 := stateReg === check1State
  io.debug.check2 := stateReg === check2State
  io.debug.check3 := stateReg === check3State
  io.debug.check4 := stateReg === check4State
  io.debug.cacheReq := stateReg === cacheReqState
  io.debug.gfxReq := stateReg === gfxReqState
  io.debug.cacheWait := stateReg === cacheWaitState
  io.debug.gfxWait := stateReg === gfxWaitState
  io.debug.fbFromDDR := stateReg === fbFromDDRState
  io.debug.fbToDDR := stateReg === fbToDDRState
  io.debug.download := stateReg === downloadState

  printf(p"DDRArbiter(state: $stateReg, nextState: $nextState, cache: $cacheBurstValue ($cacheBurstDone), cacheValid: ${io.cache.valid}, gfx: $gfxBurstValue ($gfxBurstDone), gfxValid: ${io.gfx.valid}, fbFromDDR: $fbFromDDRBurstValue ($fbFromDDRBurstDone), fbFromDDRValid: ${io.fbFromDDR.valid}, fbToDDR: $fbToDDRBurstValue ($fbToDDRBurstDone), fbToDDRWaitReq: ${io.fbToDDR.waitReq})\n")
}

object DDRArbiter {
  /** The width of the address bus */
  val ADDR_WIDTH = 32
  /** The width of the data bus */
  val DATA_WIDTH = 64
  /** The length of the cache burst */
  val CACHE_BURST_LENGTH = 4
}
