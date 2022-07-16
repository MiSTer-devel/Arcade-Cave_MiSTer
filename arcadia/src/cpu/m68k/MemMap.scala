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

package arcadia.cpu.m68k

import arcadia.Util
import arcadia.mem._
import chisel3._

/**
 * A memory map.
 *
 * Allows a CPU address space to be mapped to different memory devices.
 *
 * @param cpu The CPU IO port.
 */
class MemMap(cpu: CPUIO) {
  // Registers
  val dinReg = RegInit(0.U(CPU.DATA_WIDTH.W))
  val dtackReg = RegInit(false.B)

  // Strobe signals
  val readStrobe = Util.rising(cpu.as) && cpu.rw
  val upperWriteStrobe = cpu.as && Util.rising(cpu.uds) && !cpu.rw
  val lowerWriteStrobe = cpu.as && Util.rising(cpu.lds) && !cpu.rw
  val writeStrobe = upperWriteStrobe || lowerWriteStrobe

  /**
   * Creates a mapping for the given address.
   *
   * @param a The address.
   */
  def apply(a: Int): Mapping = apply(a.to(a))

  /**
   * Creates a mapping for the given address range.
   *
   * @param r The address range.
   */
  def apply(r: Range) = new Mapping(cpu, r)

  /**
   * Represents a memory mapped address range.
   *
   * @param cpu The CPU IO port.
   * @param r   The address range.
   */
  class Mapping(cpu: CPUIO, r: Range) {
    // The CPU address bus is only 23 bits, because the LSB is inferred from the UDS and LDS
    // signals. However, we still need to use a 24-bit value when comparing the address to a byte
    // range.
    val addr = cpu.addr ## 0.U

    // Address offset
    val offset = addr - r.start.U

    // Chip select
    val cs = Util.between(addr, r)

    /**
     * Maps an address range to the given read-write memory port.
     *
     * @param mem The memory port.
     */
    def readWriteMem(mem: MemIO): Unit = readWriteMemT(mem)(identity)

    /**
     * Maps an address range to the given read-write memory port, with an address transform.
     *
     * @param mem The memory port.
     * @param f   The address transform function.
     */
    def readWriteMemT(mem: MemIO)(f: UInt => UInt): Unit = {
      mem.rd := cs && readStrobe
      mem.wr := cs && writeStrobe
      mem.addr := f(cpu.addr)
      mem.mask := cpu.uds ## cpu.lds
      mem.din := cpu.dout
      when(cs) {
        dinReg := mem.dout
        dtackReg := true.B
      }
    }

    /**
     * Maps an address range to the given read-only memory port.
     *
     * @param mem The memory port.
     */
    def readMem(mem: AsyncReadMemIO): Unit = readMemT(mem)(identity)

    /**
     * Maps an address range to the given read-only memory port, with an address transform.
     *
     * @param mem The memory port.
     * @param f   The address transform function.
     */
    def readMemT(mem: AsyncReadMemIO)(f: UInt => UInt): Unit = {
      mem.rd := cs && readStrobe
      mem.addr := f(cpu.addr)
      when(cs && cpu.rw && mem.valid) {
        dinReg := mem.dout
        dtackReg := true.B
      }
    }

    /**
     * Maps an address range to the given write-only memory port.
     *
     * @param mem The memory port.
     */
    def writeMem(mem: WriteMemIO): Unit = writeMemT(mem)(identity)

    /**
     * Maps an address range to the given write-only memory port, with an address transform.
     *
     * @param mem The memory port.
     * @param f   The address transform function.
     */
    def writeMemT(mem: WriteMemIO)(f: UInt => UInt): Unit = {
      mem.wr := cs && writeStrobe
      mem.addr := f(cpu.addr)
      mem.mask := cpu.uds ## cpu.lds
      mem.din := cpu.dout
      when(cs && !cpu.rw) { dtackReg := true.B }
    }

    /**
     * Maps an address range to the given getter and setter functions.
     *
     * @param f The getter function.
     * @param g The setter function.
     */
    def rw(f: (UInt, UInt) => UInt)(g: (UInt, UInt, Bits) => Unit): Unit = {
      when(cs) {
        when(readStrobe) {
          dinReg := f(addr, offset)
        }.elsewhen(writeStrobe) {
          g(addr, offset, cpu.dout)
        }
        dtackReg := true.B
      }
    }

    /**
     * Maps an address range to the given getter function.
     *
     * @param f The getter function.
     */
    def r(f: (UInt, UInt) => UInt): Unit = {
      when(cs && readStrobe) {
        dinReg := f(addr, offset)
        dtackReg := true.B
      }
    }

    /**
     * Maps an address range to the given setter function.
     *
     * @param f The setter function.
     */
    def w(f: (UInt, UInt, Bits) => Unit): Unit = {
      when(cs && writeStrobe) {
        f(addr, offset, cpu.dout)
        dtackReg := true.B
      }
    }

    /**
     * Maps the address range to a stubbed memory device.
     *
     * The memory device is stubbed by reading and writing to a single register. This is useful
     * for handling ranges of the memory map that are only checked during boot, but are ignored
     * the rest of the time.
     */
    def readWriteStub(): Unit = {
      val tmp = Reg(UInt())
      rw((_, _) => tmp)((_, _, data) => tmp := data)
    }

    /** Ignores write access for the address range. */
    def nopr(): Unit = {
      r((_, _) => 0.U)
    }

    /** Ignores write access for the address range. */
    def nopw(): Unit = {
      w((_, _, _) => {})
    }

    /** Ignores read/write access for the address range. */
    def noprw(): Unit = {
      rw((_, _) => 0.U)((_, _, _) => {})
    }

    // Clear data transfer acknowledge register
    when(!cpu.as) { dtackReg := false.B }

    // Set input data bus and data transfer acknowledge from the registered values
    cpu.din := dinReg
    cpu.dtack := dtackReg
  }
}
