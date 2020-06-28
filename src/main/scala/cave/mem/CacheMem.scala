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

package cave.mem

import chisel3._
import chisel3.util._

/**
 * The CacheIO bundle extends a DecoupledIO with an address signal.
 *
 * @param addrWidth The width of the address bus.
 * @param dataWidth The width of the data bus.
 */
class CacheIO(addrWidth: Int, dataWidth: Int) extends DecoupledIO(Bits(dataWidth.W)) {
  /** address bus */
  val addr = Input(UInt(addrWidth.W))
  override def cloneType: this.type = new CacheIO(addrWidth, dataWidth).asInstanceOf[this.type]
}

/**
 * Represents the cache configuration.
 */
case class CacheConfig(addrWidth: Int, dataWidthIn: Int, dataWidthOut: Int, depth: Int) {
  def numWords = dataWidthIn / dataWidthOut
  def indexWidth = log2Ceil(depth)
  def offsetWidth = log2Ceil(numWords)
  def tagWidth = addrWidth - indexWidth - offsetWidth
  def lineWidth = wordWidth * numWords
  def wordWidth = dataWidthOut
}

/**
 * Represents the location of a word stored within the cache.
 */
class CacheAddress(private val config: CacheConfig) extends Bundle {
  /** The most significant bits of the address. */
  val tag = UInt(config.tagWidth.W)
  /** Index of the cache entry within the cache. */
  val index = UInt(config.indexWidth.W)
  /** Offset of the desired data within the cache line. */
  val offset = UInt(config.offsetWidth.W)
}

object CacheAddress {
  /** Decodes the cache address from the given value. */
  def fromValue(config: CacheConfig, data: UInt) = {
    val tagOffset = data.getWidth-1
    val indexOffset = data.getWidth-config.tagWidth-1
    val offsetOffset = data.getWidth-config.tagWidth-config.indexWidth-1

    val c = Wire(new CacheAddress(config))
    c.tag := data(tagOffset, tagOffset-config.tagWidth+1)
    c.index := data(indexOffset, indexOffset-config.indexWidth+1)
    c.offset := data(offsetOffset, offsetOffset-config.offsetWidth+1)
    c
  }
}

/**
 * Represents a single entry stored within the cache.
 */
class CacheEntry(private val config: CacheConfig) extends Bundle {
  /** The most significant bits of the address. */
  val tag = UInt(config.tagWidth.W)
  /** The cache line. */
  val line = Vec(config.numWords, UInt(config.wordWidth.W))
  /** Flag to indicate whether the cache entry is valid. */
  val valid = Bool()
}

object CacheEntry {
  /** Decodes the cache entry from the given value. */
  def fromValue(config: CacheConfig, data: UInt) = {
    val tagOffset = data.getWidth-1
    val lineOffset = data.getWidth-config.tagWidth-1
    val flagsOffset = data.getWidth-config.tagWidth-config.lineWidth-1

    val c = Wire(new CacheEntry(config))
    c.tag := data(tagOffset, tagOffset-config.tagWidth+1)
    c.line := VecInit(Seq.tabulate(config.numWords) { n =>
      data(lineOffset-(n*config.wordWidth), lineOffset-((n+1)*config.wordWidth)+1)
    }.reverse)
    c.valid := data(flagsOffset)
    c
  }
}

/**
 * Cache Memory
 *
 * @param addrWidth The width of the input address bus.
 * @param dataWidthIn The width of the input data bus.
 * @param dataWidthOut The width of the output data bus.
 * @param depth The number of cache entries.
 */
class CacheMem(addrWidth: Int, dataWidthIn: Int, dataWidthOut: Int, depth: Int = 8) extends Module {
  val io = IO(new Bundle {
    /** Memory port. */
    val mem = Flipped(new CacheIO(addrWidth, dataWidthIn))
    /** CPU port. */
    val cpu = new CacheIO(addrWidth, dataWidthOut)
  })

  val config = CacheConfig(
    addrWidth = addrWidth,
    dataWidthIn = dataWidthIn,
    dataWidthOut = dataWidthOut,
    depth = depth
  )

  // Cache entries are stored in RAM
  val mem = SyncReadMem(depth, new CacheEntry(config), SyncReadMem.WriteFirst)

  // Parse cache address from read address
  val cacheAddr = CacheAddress.fromValue(config, io.cpu.addr)

  // FSM
  val idle :: check :: load :: Nil = Enum(3)
  val stateReg = RegInit(idle)

  // Control signals
  val readEnable = stateReg === idle && io.cpu.ready
  val writeEnable = stateReg === load && io.mem.valid

  // Read cache entry from RAM
  val entry = mem.read(cacheAddr.index, readEnable)

  // Write cache entry to RAM
  when(writeEnable) {
    mem.write(cacheAddr.index, CacheEntry.fromValue(config, cacheAddr.tag ## io.mem.bits ## 1.U))
  }

  // Hit is asserted when the entry is valid, and the tags are matching
  val hit = stateReg === check && entry.valid && entry.tag === cacheAddr.tag

  printf(p"state=$stateReg, addr=$cacheAddr, entry=$entry, hit=$hit\n")

  switch(stateReg) {
    is(idle) {
      when(io.cpu.ready) {
        stateReg := check
      }
    }
    is(check) {
      when(hit) {
        stateReg := idle
      } otherwise {
        stateReg := load
      }
    }
    is(load) {
      when(io.mem.valid) {
        stateReg := check
      }
    }
  }

  // Returns a value with the given number of LSB masked off.
  private def bitmask(n: Int, width: Int) = ~((1.U(width.W) << n).asUInt()-1.U)

  // Outputs
  io.mem.addr := io.cpu.addr & bitmask(config.offsetWidth, config.addrWidth).asUInt()
  io.mem.ready := !hit
  io.cpu.bits := entry.line(cacheAddr.offset)
  io.cpu.valid := hit
}
