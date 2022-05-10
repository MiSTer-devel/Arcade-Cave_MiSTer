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

package axon.mem.cache

import axon.mem._
import chisel3._
import chisel3.util._

/**
 * A direct-mapped cache memory.
 *
 * A cache can be used to speed up access to a high latency memory device (e.g. SDRAM), by keeping a
 * copy of frequently accessed data. It supports both read and write operations.
 *
 * Cache entries are stored in BRAM, which means that reading/writing to a memory address that is
 * already stored in the cache (cache hit) only takes one clock cycle.
 *
 * Reading/writing to memory address that isn't stored in the cache (cache miss) is slower, as the
 * data must first be fetched from the high latency memory device (line fill). Although, subsequent
 * reads/writes to the same address will be much faster.
 *
 * @param config The cache configuration.
 */
class Cache(config: Config) extends Module {
  // Sanity check
  assert(config.inDataWidth >= 8, "Input data width must be at least 8")
  assert(config.outDataWidth >= 8, "Output data width must be at least 8")

  val io = IO(new Bundle {
    /** Enable the cache */
    val enable = Input(Bool())
    /** Input port */
    val in = Flipped(AsyncReadWriteMemIO(config.inAddrWidth, config.inDataWidth))
    /** Output port */
    val out = BurstReadWriteMemIO(config.outAddrWidth, config.outDataWidth)
    /** Debug port */
    val debug = Output(new Bundle {
      val idle = Bool()
      val latch = Bool()
      val check = Bool()
      val fill = Bool()
      val fillWait = Bool()
      val evict = Bool()
      val evictWait = Bool()
      val merge = Bool()
      val write = Bool()
    })
  })

  // States
  object State {
    val init :: idle :: latch :: check :: fill :: fillWait :: evict :: evictWait :: merge :: write :: Nil = Enum(10)
  }

  // State register
  val nextState = Wire(UInt())
  val stateReg = RegNext(nextState, State.init)

  // Cache memory request
  val request = MemRequest(io.in.rd, io.in.wr, Address(io.in.addr)(config))

  // Assert the latch signal when a request should be latched
  val latch = nextState === State.latch

  // Request register
  val requestReg = RegEnable(request, latch)

  // Data register
  val dataReg = RegEnable(io.in.din, latch)

  // Offset register
  val offsetReg = {
    val addr = io.in.addr >> log2Ceil(config.inBytes)
    val offset = addr(log2Up(config.inWords) - 1, 0)
    RegEnable(offset, latch)
  }

  // Cache entry memory
  val cacheEntryMem = SyncReadMem(config.depth, Bits(new Entry(config).getWidth.W))
  val cacheEntry = cacheEntryMem.read(request.addr.index).asTypeOf(new Entry(config))
  val cacheEntryReg = RegEnable(cacheEntry, stateReg === State.latch)

  // Assert the burst counter enable signal as words are bursted from memory
  val burstCounterEnable = {
    val fill = stateReg === State.fillWait && io.out.valid
    val evict = (stateReg === State.evict || stateReg === State.evictWait) && !io.out.waitReq
    fill || evict
  }

  // Counters
  val (initCounter, initCounterWrap) = Counter(stateReg === State.init, config.depth)
  val (burstCounter, burstCounterWrap) = Counter(burstCounterEnable, config.lineWidth)

  // Control signals
  val start = io.enable && request.valid
  val dirty = cacheEntryReg.dirty && cacheEntryReg.tag =/= requestReg.addr.tag
  val hit = cacheEntryReg.valid && cacheEntryReg.tag === requestReg.addr.tag
  val miss = !hit

  // Deassert the wait signal during the idle state, or at the end of a request to allow chaining
  val waitReq = {
    val idle = stateReg === State.idle
    val read = stateReg === State.check && hit && requestReg.rd
    val write = stateReg === State.write
    !(idle || read || write)
  }

  // For a wrapping burst, the word done signal is asserted as soon as enough output words have
  // been bursted to fill an input word. Otherwise, for a non-wrapping burst we just wait until the
  // end of the burst.
  val wordDone = if (config.wrapping) {
    burstCounter === (config.inOutDataWidthRatio - 1).U
  } else {
    burstCounterWrap
  }

  // Assert the valid signal during a cache hit, or after a word has been filled
  val valid = {
    val hitValid = stateReg === State.check && hit
    val fillValid = stateReg === State.fillWait && wordDone && io.out.valid
    requestReg.rd && (hitValid || RegNext(fillValid, false.B))
  }

  // Calculate the output byte address
  val outAddr = {
    val fillAddr = if (!config.wrapping) {
      Address(requestReg.addr.tag, requestReg.addr.index, 0.U)(config)
    } else {
      requestReg.addr
    }
    val evictAddr = Address(cacheEntryReg.tag, requestReg.addr.index, 0.U)(config)
    val addr = Mux(stateReg === State.fill, fillAddr, evictAddr).asUInt
    (addr << log2Ceil(config.outBytes)).asUInt
  }

  // Write cache entry
  when(stateReg === State.init || stateReg === State.write) {
    val index = Mux(stateReg === State.write, requestReg.addr.index, initCounter)
    val data = Mux(stateReg === State.write, cacheEntryReg.asUInt, 0.U)
    cacheEntryMem.write(index, data)
  }

  // Fill cache line as words are bursted
  when(stateReg === State.fillWait && io.out.valid) {
    val fillOffset = if (config.wrapping) requestReg.addr.offset else 0.U
    cacheEntryReg.fill(requestReg.addr.tag, fillOffset + burstCounter, io.out.dout)
  }

  // Merge the input data with the cache line
  when(stateReg === State.merge) { cacheEntryReg.merge(offsetReg, dataReg) }

  // Default to the previous state
  nextState := stateReg

  // FSM
  switch(stateReg) {
    // Initialize cache
    is(State.init) {
      when(initCounterWrap) { nextState := State.idle }
    }

    // Wait for a request
    is(State.idle) {
      when(start) { nextState := State.latch }
    }

    // Latch cache entry
    is(State.latch) { nextState := State.check }

    // Check cache entry
    is(State.check) {
      when(hit && requestReg.rd) {
        nextState := Mux(start, State.latch, State.idle)
      }.elsewhen(hit && requestReg.wr) {
        nextState := State.merge
      }.elsewhen(dirty) {
        nextState := State.evict
      }.elsewhen(miss) {
        nextState := State.fill
      }
    }

    // Fill a cache line
    is(State.fill) {
      when(!io.out.waitReq) { nextState := State.fillWait }
    }

    // Wait for a line file
    is(State.fillWait) {
      when(burstCounterWrap) { nextState := Mux(requestReg.wr, State.merge, State.write) }
    }

    // Evict a dirty cache entry
    is(State.evict) {
      when(!io.out.waitReq) { nextState := State.evictWait }
    }

    // Wait for an eviction
    is(State.evictWait) {
      when(burstCounterWrap) { nextState := State.fill }
    }

    // Merge a word with the cache line
    is(State.merge) { nextState := State.write }

    // Write cache entry
    is(State.write) {
      nextState := Mux(start, State.latch, State.idle)
    }
  }

  // Outputs
  io.in.waitReq := waitReq
  io.in.valid := valid
  io.in.dout := cacheEntryReg.inWord(offsetReg)
  io.out.rd := stateReg === State.fill
  io.out.wr := stateReg === State.evict || stateReg === State.evictWait
  io.out.burstLength := config.lineWidth.U
  io.out.addr := outAddr
  io.out.mask := Fill(config.outBytes, 1.U)
  io.out.din := cacheEntryReg.outWord(burstCounter)
  io.debug.idle := stateReg === State.idle
  io.debug.latch := stateReg === State.latch
  io.debug.check := stateReg === State.check
  io.debug.fill := stateReg === State.fill
  io.debug.fillWait := stateReg === State.fillWait
  io.debug.evict := stateReg === State.evict
  io.debug.evictWait := stateReg === State.evictWait
  io.debug.merge := stateReg === State.merge
  io.debug.write := stateReg === State.write

  printf(p"CacheMem(state: $stateReg, tag: ${ requestReg.addr.tag }, index: ${ requestReg.addr.index }, offset: $offsetReg, inWords: ${ cacheEntryReg.line.inWords } (0x${ Hexadecimal(cacheEntryReg.line.inWords.asUInt) }), outWords: ${ cacheEntryReg.line.outWords } (0x${ Hexadecimal(cacheEntryReg.line.outWords.asUInt) }))\n")
}
