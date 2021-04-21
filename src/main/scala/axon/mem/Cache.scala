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
 * Represents the cache configuration.
 *
 * @param inAddrWidth  The width of the input address bus.
 * @param inDataWidth  The width of the input data bus.
 * @param outAddrWidth The width of the output address bus.
 * @param outDataWidth The width of the output data bus.
 * @param depth        The number of entries in the cache.
 * @param lineWidth    The number of words in a cache line.
 * @param wrapping     A boolean indicating whether burst wrapping should be enabled for the cache.
 *                     When a wrapping burst reaches a burst boundary, the address wraps back to the
 *                     previous burst boundary.
 */
case class CacheConfig(inAddrWidth: Int,
                       inDataWidth: Int,
                       outAddrWidth: Int,
                       outDataWidth: Int,
                       lineWidth: Int,
                       depth: Int,
                       wrapping: Boolean = false) {
  /** The width of a cache address index */
  val indexWidth = log2Ceil(depth)
  /** The width of a cache address offset */
  val offsetWidth = log2Ceil(lineWidth)
  /** The width of a cache tag */
  val tagWidth = inAddrWidth - indexWidth - offsetWidth
  /** The number of input words in a cache line */
  val inWords = outDataWidth * lineWidth / inDataWidth
  /** The number of bytes in an input word */
  val inBytes = inDataWidth / 8
  /** The number of bytes in an output word */
  val outBytes = outDataWidth / 8
  /** The number of output words that fit into a single input word */
  val inOutDataWidthRatio = if (inDataWidth > outDataWidth) inDataWidth / outDataWidth else 1
}

/**
 * A cache line is stored internally as a vector of words that has the same width as the output data
 * bus.
 *
 * A cache line can also be represented a vector of input words, by rearranging the byte grouping.
 */
class CacheLine(private val config: CacheConfig) extends Bundle {
  val words: Vec[Bits] = Vec(config.lineWidth, Bits(config.outDataWidth.W))

  /** Returns the cache line represented as a vector input words */
  def inWords: Vec[Bits] = words.asTypeOf(Vec(config.inWords, Bits(config.inDataWidth.W)))

  /** The output words in the cache line */
  def outWords = words
}

/** Represents the location of a word stored in the cache. */
class CacheAddress(private val config: CacheConfig) extends Bundle {
  /** The most significant bits of the address */
  val tag = UInt(config.tagWidth.W)
  /** The index of the cache entry within the cache */
  val index = UInt(config.indexWidth.W)
  /** The offset of the word within the cache line */
  val offset = UInt(config.offsetWidth.W)
}

object CacheAddress {
  /**
   * Constructs a cache address.
   *
   * @param tag    The tag value.
   * @param index  The index value.
   * @param offset The offset value.
   * @param config The cache configuration.
   */
  def apply(tag: UInt, index: UInt, offset: UInt)(config: CacheConfig): CacheAddress = {
    val wire = Wire(new CacheAddress(config))
    wire.tag := tag
    wire.index := index
    wire.offset := offset
    wire
  }

  /**
   * Constructs a cache address from a byte address.
   *
   * @param addr   The byte address.
   * @param config The cache configuration.
   */
  def apply(addr: UInt)(config: CacheConfig): CacheAddress =
    (addr >> log2Ceil(config.outBytes)).asTypeOf(new CacheAddress(config))
}

/** Represents an entry stored in the cache. */
class CacheEntry(private val config: CacheConfig) extends Bundle {
  /** The cache line */
  val line = new CacheLine(config)
  /** The most significant bits of the address */
  val tag = UInt(config.tagWidth.W)
  /** Flag to indicate whether the cache entry is valid */
  val valid = Bool()
  /** Flag to indicate whether the cache entry is dirty */
  val dirty = Bool()

  /** Returns the input word at the given offset. */
  def inWord(offset: UInt): Bits = line.inWords((~offset).asUInt)

  /** Returns the output word at the given offset. */
  def outWord(offset: UInt): Bits = line.outWords((~offset).asUInt)

  /**
   * Fills the cache line with the given data, and marks the line as valid.
   *
   * @param tag    The cache line tag value.
   * @param offset The address offset.
   * @param data   The data.
   */
  def fill(tag: UInt, offset: UInt, data: Bits) = {
    line.words((~offset).asUInt) := data
    this.tag := tag
    valid := true.B
  }

  /**
   * Merges the given data with the cache line, and marks the line as dirty.
   *
   * @param offset The address offset.
   * @param data   The data.
   */
  def merge(offset: UInt, data: Bits) = {
    val words = WireInit(line.inWords)
    words((~offset).asUInt) := data
    line.words := words.asTypeOf(chiselTypeOf(line.words))
    dirty := true.B
  }
}

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
class Cache(config: CacheConfig) extends Module {
  // Sanity check
  assert(config.inDataWidth >= 8, "Input data width must be at least 8")
  assert(config.outDataWidth >= 8, "Output data width must be at least 8")

  val io = IO(new Bundle {
    /** Input port */
    val in = Flipped(AsyncReadWriteMemIO(config.inAddrWidth, config.inDataWidth))
    /** Output port */
    val out = BurstReadWriteMemIO(config.outAddrWidth, config.outDataWidth)
    /** Output address offset */
    val offset = Input(UInt(config.outAddrWidth.W))
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

  // Assert the latch signal when a request should be latched
  val latch = nextState === State.latch

  // Request register
  val request = MemRequest(io.in.rd, io.in.wr, CacheAddress(io.in.addr)(config))
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
  val cacheEntryMem = SyncReadMem(config.depth, Bits(new CacheEntry(config).getWidth.W))
  val cacheEntry = cacheEntryMem.read(request.addr.index).asTypeOf(new CacheEntry(config))
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
  val dirty = cacheEntryReg.dirty && cacheEntryReg.tag =/= requestReg.addr.tag
  val hit = cacheEntryReg.valid && cacheEntryReg.tag === requestReg.addr.tag
  val miss = !hit
  val waitReq = !(stateReg === State.idle || latch)

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
      CacheAddress(requestReg.addr.tag, requestReg.addr.index, 0.U)(config)
    } else {
      requestReg.addr
    }
    val evictAddr = CacheAddress(cacheEntryReg.tag, requestReg.addr.index, 0.U)(config)
    val addr = Mux(stateReg === State.fill, fillAddr, evictAddr).asUInt
    (addr << log2Ceil(config.outBytes)).asUInt + io.offset
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
      when(request.valid) { nextState := State.latch }
    }

    // Latch cache entry
    is(State.latch) { nextState := State.check }

    // Check cache entry
    is(State.check) {
      when(hit && requestReg.rd) {
        nextState := Mux(request.valid, State.latch, State.idle)
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
    is(State.write) { nextState := Mux(request.valid, State.latch, State.idle) }
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
