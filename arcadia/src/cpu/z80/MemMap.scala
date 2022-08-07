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

package arcadia.cpu.z80

import arcadia.Util
import arcadia.mem._
import chisel3._

/**
 * A memory map.
 *
 * Allows a CPU address space to be mapped to different memory devices.
 *
 * The T80 module has a bug where the read enable signal isn't asserted until the input data is
 * latched, instead of being asserted during the previous clock cycle. This means that we don't know
 * a memory read will occur until it happens.
 *
 * To work around this issue, we need to prefetch all values on the CPU address bus (unless it's a
 * memory refresh). This ensures the data is ready when the read enable signal is asserted.
 *
 * @param cpu The CPU IO port.
 */
class MemMap(cpu: CPUIO) {
  /**
   * Create a memory map for the given address.
   *
   * @param a The address.
   */
  def apply(a: Int): Mapping = apply(a.to(a))

  /**
   * Create a memory map for the given address range.
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
    // Address offset
    val offset = cpu.addr - r.start.U

    // Chip select
    val cs = Util.between(cpu.addr, r)

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
      mem.rd := cs && !cpu.rfsh
      mem.wr := cs && cpu.mreq && cpu.wr
      mem.addr := f(cpu.addr)
      mem.din := cpu.dout
      when(cs && cpu.mreq && cpu.rd) { cpu.din := mem.dout }
    }

    /**
     * Maps an address range to the given read-only memory port.
     *
     * @param mem The memory port.
     */
    def readMem(mem: ReadMemIO): Unit = readMemT(mem)(identity)

    /**
     * Maps an address range to the given read-only memory port, with an address transform.
     *
     * @param mem The memory port.
     * @param f   The address transform function.
     */
    def readMemT(mem: ReadMemIO)(f: UInt => UInt): Unit = {
      mem.rd := cs && !cpu.rfsh
      mem.addr := f(cpu.addr)
      when(cs && cpu.mreq && cpu.rd) { cpu.din := mem.dout }
    }

    /**
     * Maps an address range to the given getter and setter functions.
     *
     * @param f The getter function.
     * @param g The setter function.
     */
    def rw(f: (UInt, UInt) => UInt)(g: (UInt, UInt, Bits) => Unit): Unit = {
      when(cs && cpu.mreq) {
        when(cpu.rd) {
          cpu.din := f(cpu.addr, offset)
        }.elsewhen(cpu.wr) {
          g(cpu.addr, offset, cpu.dout)
        }
      }
    }

    /**
     * Maps an address range to the given getter function.
     *
     * @param f The getter function.
     */
    def r(f: (UInt, UInt) => Bits): Unit = {
      when(cs && cpu.mreq && cpu.rd) { cpu.din := f(cpu.addr, offset) }
    }

    /**
     * Maps an address range to the given setter function.
     *
     * @param f The setter function.
     */
    def w(f: (UInt, UInt, Bits) => Unit): Unit = {
      when(cs && cpu.mreq && cpu.wr) { f(cpu.addr, offset, cpu.dout) }
    }

    /** Ignores write access for the address range. */
    def nopw(): Unit = {
      w((_, _, _) => {})
    }

    /** Ignores read/write access for the address range. */
    def noprw(): Unit = {
      rw((_, _) => 0.U)((_, _, _) => {})
    }
  }
}
