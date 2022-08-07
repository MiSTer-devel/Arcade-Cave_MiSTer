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

import chisel3._
import chisel3.util._

/** Defines the address and data width of a data bus. */
trait BusConfig {
  /** The width of the address bus. */
  def addrWidth: Int
  /** The width of the data bus. */
  def dataWidth: Int
  /** The width of the data mask (i.e. the number of bytes that can be masked when writing data). */
  def maskWidth = dataWidth / 8
}

/** Defines interface conversion methods. */
trait ConvertMemIO {
  /** Converts the interface to synchronous read-write. */
  def asMemIO: MemIO
}

/**
 * A simple flow control interface for reading from synchronous memory.
 *
 * @param addrWidth The width of the address bus.
 * @param dataWidth The width of the data bus.
 */
class ReadMemIO(val addrWidth: Int, val dataWidth: Int) extends Bundle with BusConfig with ConvertMemIO {
  /** Read enable */
  val rd = Output(Bool())
  /** Address bus */
  val addr = Output(UInt(addrWidth.W))
  /** Data bus */
  val dout = Input(Bits(dataWidth.W))

  def this(config: BusConfig) = this(config.addrWidth, config.dataWidth)

  /** Converts the interface to synchronous read-write. */
  def asMemIO: MemIO = {
    val mem = Wire(Flipped(MemIO(this)))
    mem.rd := rd
    mem.wr := false.B
    mem.addr := addr
    mem.mask := DontCare
    mem.din := DontCare
    mem
  }

  /** Sets default values for all the signals. */
  def default(): Unit = {
    rd := false.B
    addr := DontCare
  }

  /**
   * Maps the address using the given function.
   *
   * @param f The transform function.
   */
  def mapAddr(f: UInt => UInt): ReadMemIO = {
    val mem = Wire(Flipped(ReadMemIO(f(addr).getWidth, this.dataWidth)))
    mem.rd := rd
    mem.addr := f(addr)
    dout := mem.dout
    mem
  }

  /**
   * Maps the data using the given function.
   *
   * @param f The transform function.
   */
  def mapData(f: Bits => Bits): ReadMemIO = {
    val mem = Wire(Flipped(ReadMemIO(this)))
    mem.rd := rd
    mem.addr := addr
    dout := f(mem.dout)
    mem
  }
}

object ReadMemIO {
  def apply(addrWidth: Int, dataWidth: Int): ReadMemIO = new ReadMemIO(addrWidth, dataWidth)

  def apply(config: BusConfig) = new ReadMemIO(config)

  /**
   * Multiplexes requests from two read-only memory interfaces to a single read-only memory
   * interface.
   *
   * @param select Selects between the two memory interfaces.
   * @param a      The first interface.
   * @param b      The second interface.
   */
  def mux(select: Bool, a: ReadMemIO, b: ReadMemIO): ReadMemIO = {
    val mem = Wire(chiselTypeOf(a))
    mem.rd := Mux(select, a.rd, b.rd)
    mem.addr := Mux(select, a.addr, b.addr)
    a.dout := mem.dout
    b.dout := mem.dout
    mem
  }

  /**
   * Multiplexes requests from read-only memory interfaces to a single read-only memory
   * interface.
   *
   * @param key     The key to used to select the interface.
   * @param default The default interface.
   * @param mapping A list of key-interface pairs.
   */
  def muxLookup[K <: UInt](key: K, default: ReadMemIO, mapping: Seq[(K, ReadMemIO)]): ReadMemIO = {
    val mem = Wire(chiselTypeOf(mapping.head._2))
    val rdMap = mapping.map(a => a._1 -> a._2.rd)
    val addrMap = mapping.map(a => a._1 -> a._2.addr)
    mem.rd := MuxLookup(key, default.rd, rdMap)
    mem.addr := MuxLookup(key, default.addr, addrMap)
    default.dout := mem.dout
    mapping.foreach { case (_, out) =>
      out.dout := mem.dout
    }
    mem
  }

  /**
   * Demultiplexes requests from a single read-only memory interface to multiple read-only memory
   * interfaces. The request is routed to the interface matching a given key.
   *
   * @param key  The key to used to select the interface.
   * @param outs A list of key-interface pairs.
   */
  def demux[K <: UInt](key: K, outs: Seq[(K, ReadMemIO)]): ReadMemIO = {
    val mem = Wire(chiselTypeOf(outs.head._2))
    outs.foreach { case (k, out) =>
      out.rd := k === key && mem.rd
      out.addr := mem.addr
    }
    val doutMap = outs.map(a => a._1 -> a._2.dout)
    mem.dout := MuxLookup(key, 0.U, doutMap)
    mem
  }
}

/**
 * A simple flow control interface for writing to synchronous memory.
 *
 * @param addrWidth The width of the address bus.
 * @param dataWidth The width of the data bus.
 */
class WriteMemIO(val addrWidth: Int, val dataWidth: Int) extends Bundle with BusConfig with ConvertMemIO {
  /** Write enable */
  val wr = Output(Bool())
  /** Address bus */
  val addr = Output(UInt(addrWidth.W))
  /** Byte mask */
  val mask = Output(Bits(maskWidth.W))
  /** Data bus */
  val din = Output(Bits(dataWidth.W))

  def this(config: BusConfig) = this(config.addrWidth, config.dataWidth)

  /** Converts the interface to synchronous read-write. */
  def asMemIO: MemIO = {
    val mem = Wire(Flipped(MemIO(this)))
    mem.rd := false.B
    mem.wr := wr
    mem.addr := addr
    mem.mask := mask
    mem.din := din
    mem
  }

  /** Sets default values for all the signals. */
  def default(): Unit = {
    wr := false.B
    addr := DontCare
    mask := DontCare
    din := DontCare
  }

  /**
   * Maps the address using the given function.
   *
   * @param f The transform function.
   */
  def mapAddr(f: UInt => UInt): WriteMemIO = {
    val mem = Wire(Flipped(WriteMemIO(f(addr).getWidth, this.dataWidth)))
    mem.wr := wr
    mem.addr := f(addr)
    mem.mask := mask
    mem.din := din
    mem
  }

  /**
   * Maps the data using the given function.
   *
   * @param f The transform function.
   */
  def mapData(f: Bits => Bits): WriteMemIO = {
    val mem = Wire(Flipped(WriteMemIO(this)))
    mem.wr := wr
    mem.addr := addr
    mem.mask := mask
    mem.din := f(din)
    mem
  }
}

object WriteMemIO {
  def apply(addrWidth: Int, dataWidth: Int): WriteMemIO = new WriteMemIO(addrWidth, dataWidth)

  def apply(config: BusConfig) = new WriteMemIO(config)

  /**
   * Multiplexes requests from multiple write-only memory interface to a single write-only memory interfaces. The
   * request is routed to the first enabled interface.
   *
   * @param outs A list of enable-interface pairs.
   */
  def mux[K <: UInt](key: K, outs: Seq[(K, WriteMemIO)]): WriteMemIO = {
    val mem = Wire(chiselTypeOf(outs.head._2))
    val writeMap = outs.map(a => a._1 -> a._2.wr)
    val addrMap = outs.map(a => a._1 -> a._2.addr)
    val maskMap = outs.map(a => a._1 -> a._2.mask)
    val dataMap = outs.map(a => a._1 -> a._2.din)
    mem.wr := MuxLookup(key, false.B, writeMap)
    mem.addr := MuxLookup(key, DontCare, addrMap)
    mem.mask := MuxLookup(key, DontCare, maskMap)
    mem.din := MuxLookup(key, DontCare, dataMap)
    mem
  }
}

/**
 * A simple flow control interface for reading and writing to synchronous memory.
 *
 * @param addrWidth The width of the address bus.
 * @param dataWidth The width of the data bus.
 */
class MemIO(val addrWidth: Int, val dataWidth: Int) extends Bundle with BusConfig with ConvertMemIO {
  /** Read enable */
  val rd = Output(Bool())
  /** Write enable */
  val wr = Output(Bool())
  /** Address bus */
  val addr = Output(UInt(addrWidth.W))
  /** Byte mask */
  val mask = Output(Bits(maskWidth.W))
  /** Data input bus */
  val din = Output(Bits(dataWidth.W))
  /** Data output bus */
  val dout = Input(Bits(dataWidth.W))

  def this(config: BusConfig) = this(config.addrWidth, config.dataWidth)

  /** Converts the interface to synchronous read-only. */
  def asReadMemIO: ReadMemIO = {
    val mem = Wire(Flipped(ReadMemIO(this)))
    rd := mem.rd
    wr := false.B
    addr := mem.addr
    mask := DontCare
    din := DontCare
    mem.dout := dout
    mem
  }

  /** Converts the interface to synchronous write-only. */
  def asWriteMemIO: WriteMemIO = {
    val mem = Wire(Flipped(WriteMemIO(this)))
    rd := false.B
    wr := mem.wr
    addr := mem.addr
    mask := mem.mask
    din := mem.din
    mem
  }

  /** Converts the interface to synchronous read-write. */
  def asMemIO: MemIO = this

  /** Sets default values for all the signals. */
  def default(): Unit = {
    rd := false.B
    wr := false.B
    addr := DontCare
    mask := DontCare
    din := DontCare
  }

  /**
   * Maps the address using the given function.
   *
   * @param f The transform function.
   */
  def mapAddr(f: UInt => UInt): MemIO = {
    val mem = Wire(Flipped(MemIO(f(addr).getWidth, this.dataWidth)))
    mem.rd := rd
    mem.wr := wr
    mem.addr := f(addr)
    mem.mask := mask
    mem.din := din
    dout := mem.dout
    mem
  }

  /**
   * Maps the data using the given function.
   *
   * @param f The transform function.
   */
  def mapData(f: Bits => Bits): MemIO = {
    val mem = Wire(Flipped(MemIO(this)))
    mem.rd := rd
    mem.wr := wr
    mem.addr := addr
    mem.mask := mask
    mem.din := f(din)
    dout := f(mem.dout)
    mem
  }
}

object MemIO {
  def apply(addrWidth: Int, dataWidth: Int): MemIO = new MemIO(addrWidth, dataWidth)

  def apply(config: BusConfig) = new MemIO(config)
}
