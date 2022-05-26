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
 * A 2-way set-associative cache memory.
 *
 * A cache can be used to speed up access to a high latency memory device (e.g. SDRAM), by keeping a
 * copy of frequently accessed data.
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
class CacheMem(config: Config) extends Module {
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
      val check = Bool()
      val fill = Bool()
      val fillWait = Bool()
      val evict = Bool()
      val evictWait = Bool()
      val merge = Bool()
      val write = Bool()
      val lru = Bits(config.depth.W)
    })
  })

  // States
  object State {
    val init :: idle :: check :: fill :: fillWait :: evict :: evictWait :: merge :: write :: Nil = Enum(9)
  }

  // State register
  val stateReg = RegInit(State.init)

  // Start request flag
  val start = Wire(Bool())

  // Convert input address to a word offset
  val offsetReg = {
    val addr = io.in.addr >> log2Ceil(config.inBytes)
    val offset = addr(log2Up(config.inWords) - 1, 0)
    RegEnable(offset, start)
  }

  // Cache request
  val request = MemRequest(io.in.rd, io.in.wr, Address(config, io.in.addr))
  val requestReg = RegEnable(request, start)

  // Data registers
  val dataReg = RegEnable(io.in.din, start)
  val doutReg = Reg(Bits(config.inDataWidth.W))
  val validReg = RegNext(false.B)

  // Least recently used register
  val lruReg = Reg(Bits(config.depth.W))

  // Cache way register
  val nextWay = Wire(Bool())
  val wayReg = RegNext(nextWay)

  // Cache entry memory
  val cacheEntryMemA = SyncReadMem(config.depth, new Entry(config))
  val cacheEntryMemB = SyncReadMem(config.depth, new Entry(config))

  // Cache entry for the current index
  val cacheEntryA = cacheEntryMemA.read(request.addr.index)
  val cacheEntryB = cacheEntryMemB.read(request.addr.index)

  // Latch cache entry for the current way during the check state. This prevents the cache entry
  // register from changing during a request.
  val cacheEntryReg = RegEnable(Mux(nextWay, cacheEntryB, cacheEntryA), stateReg === State.check)

  // Write the cache entry
  val nextCacheEntry = Mux(stateReg === State.write, cacheEntryReg, Entry.zero(config))

  when(stateReg === State.init || (stateReg === State.write && !wayReg)) {
    cacheEntryMemA.write(requestReg.addr.index, nextCacheEntry)
  }

  when(stateReg === State.init || (stateReg === State.write && wayReg)) {
    cacheEntryMemB.write(requestReg.addr.index, nextCacheEntry)
  }

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
  start := io.enable && request.valid && stateReg === State.idle
  val hitA = cacheEntryA.isHit(requestReg.addr)
  val hitB = cacheEntryB.isHit(requestReg.addr)
  val hit = hitA || hitB
  val miss = !hit
  val dirtyA = cacheEntryA.isDirty(requestReg.addr)
  val dirtyB = cacheEntryB.isDirty(requestReg.addr)
  val dirty = (!wayReg && dirtyA) || (wayReg && dirtyB)

  // For a wrapping burst, the word done signal is asserted as soon as enough output words have
  // been bursted to fill an input word. Otherwise, for a non-wrapping burst we need to wait until
  // the burst is complete.
  val wordDone = if (config.wrapping) {
    burstCounter === (config.inOutDataWidthRatio - 1).U
  } else {
    burstCounterWrap
  }

  // Calculate the output byte address
  val outAddr = {
    val fillAddr = if (!config.wrapping) {
      Address(config, requestReg.addr.tag, requestReg.addr.index, 0.U)
    } else {
      requestReg.addr
    }
    val evictAddr = Address(config, cacheEntryReg.tag, requestReg.addr.index, 0.U)
    val addr = Mux(stateReg === State.fill, fillAddr, evictAddr).asUInt
    (addr << log2Ceil(config.outBytes)).asUInt
  }

  // Latch current cache way when a request is started
  nextWay := Mux(start, lruReg(request.addr.index), wayReg)

  // Fill the cache line as words are bursted from memory
  when(stateReg === State.fillWait && io.out.valid) {
    val n = if (config.wrapping) requestReg.addr.offset + burstCounter else burstCounter
    val entry = cacheEntryReg.fill(requestReg.addr.tag, n, io.out.dout)
    cacheEntryReg := entry
    doutReg := entry.inWord(offsetReg)
    validReg := requestReg.rd && wordDone
  }

  // Merge the input data with the cache entry
  when(stateReg === State.merge) {
    stateReg := State.write
    cacheEntryReg := cacheEntryReg.merge(offsetReg, dataReg)
  }

  def onHit() = {
    when(requestReg.rd) {
      stateReg := State.idle
      doutReg := Mux(hitA, cacheEntryA.inWord(offsetReg), cacheEntryB.inWord(offsetReg))
      validReg := true.B
    }.otherwise {
      stateReg := State.merge
    }
    lruReg := lruReg.bitSet(requestReg.addr.index, hitA)
    nextWay := !hitA
  }

  def onMiss() = {
    stateReg := State.fill
    lruReg := lruReg.bitSet(requestReg.addr.index, !wayReg)
  }

  def onDirty() = {
    stateReg := State.evict
    lruReg := lruReg.bitSet(requestReg.addr.index, !wayReg)
  }

  // FSM
  switch(stateReg) {
    // Initialize cache
    is(State.init) {
      when(initCounterWrap) { stateReg := State.idle }
    }

    // Wait for a request
    is(State.idle) {
      when(start) { stateReg := State.check }
    }

    // Check cache entry
    is(State.check) {
      when(hit) { onHit() }.elsewhen(dirty) { onDirty() }.elsewhen(miss) { onMiss() }
    }

    // Fill a cache line
    is(State.fill) {
      when(!io.out.waitReq) { stateReg := State.fillWait }
    }

    // Wait for a line file
    is(State.fillWait) {
      when(burstCounterWrap) {
        stateReg := Mux(requestReg.wr, State.merge, State.write)
      }
    }

    // Evict a dirty cache entry
    is(State.evict) {
      when(!io.out.waitReq) { stateReg := State.evictWait }
    }

    // Wait for an eviction
    is(State.evictWait) {
      when(burstCounterWrap) { stateReg := State.fill }
    }

    // Merge a word with the cache line
    is(State.merge) { stateReg := State.write }

    // Write cache entry
    is(State.write) { stateReg := State.idle }
  }

  // Outputs
  io.in.waitReq := stateReg =/= State.idle
  io.in.valid := validReg
  io.in.dout := doutReg
  io.out.rd := stateReg === State.fill
  io.out.wr := stateReg === State.evict || stateReg === State.evictWait
  io.out.burstLength := config.lineWidth.U
  io.out.addr := outAddr
  io.out.mask := Fill(config.outBytes, 1.U)
  io.out.din := cacheEntryReg.outWord(burstCounter)
  io.debug.idle := stateReg === State.idle
  io.debug.check := stateReg === State.check
  io.debug.fill := stateReg === State.fill
  io.debug.fillWait := stateReg === State.fillWait
  io.debug.evict := stateReg === State.evict
  io.debug.evictWait := stateReg === State.evictWait
  io.debug.merge := stateReg === State.merge
  io.debug.write := stateReg === State.write
  io.debug.lru := lruReg

  printf(p"CacheMem(state: $stateReg, way: $wayReg, lru: 0x${ Hexadecimal(lruReg) }, hitA: $hitA, hitB: $hitB, dirtyA: $dirtyA, dirtyB: $dirtyB, tag: 0x${ Hexadecimal(requestReg.addr.tag) }, index: ${ requestReg.addr.index }, offset: $offsetReg, wordsA: 0x${ Hexadecimal(cacheEntryA.line.inWords.asUInt) } (0x${ Hexadecimal(cacheEntryA.tag) }), wordsB: 0x${ Hexadecimal(cacheEntryB.line.inWords.asUInt) } (0x${ Hexadecimal(cacheEntryB.tag) }))\n")
}
