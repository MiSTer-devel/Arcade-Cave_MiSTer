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

package axon.cpu.m68k

import axon.Util
import axon.mem._
import chisel3._

/**
 * A memory map.
 *
 * Allows a CPU address space to be mapped to different memory devices.
 *
 * @param cpu The CPU IO port.
 */
class MemMap(cpu: CPUIO) {
  private val readStrobe = Util.rising(cpu.as) && cpu.rw
  private val upperWriteStrobe = Util.rising(cpu.uds) && !cpu.rw
  private val lowerWriteStrobe = Util.rising(cpu.lds) && !cpu.rw
  private val addr = cpu.addr ## 0.U

  /**
   * Create a memory map for the given address.
   *
   * @param a The address.
   */
  def apply(a: Int) = new Mapping(cpu, Range(a, a))

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
    /**
     * Maps an address range to the given read-write memory port.
     *
     * @param mem The memory port.
     */
    def readWriteMem(mem: ReadWriteMemIO): Unit = readWriteMemT(mem)(identity)

    /**
     * Maps an address range to the given read-write memory port, with an address transform.
     *
     * @param mem The memory port.
     * @param f   The address transform function.
     */
    def readWriteMemT(mem: ReadWriteMemIO)(f: UInt => UInt): Unit = {
      val cs = Util.between(addr, r)
      mem.rd := cs && readStrobe
      mem.wr := cs && (upperWriteStrobe || lowerWriteStrobe)
      mem.addr := f(cpu.addr)
      mem.mask := cpu.uds ## cpu.lds
      mem.din := cpu.dout
      when(cs) {
        cpu.din := mem.dout
        cpu.dtack := true.B
      }
    }

    /**
     * Maps an address range to the given read-only memory port.
     *
     * @param mem The memory port.
     */
    def readMem(mem: ValidReadMemIO): Unit = readMemT(mem)(identity)

    /**
     * Maps an address range to the given read-only memory port, with an address transform.
     *
     * @param mem The memory port.
     * @param f   The address transform function.
     */
    def readMemT(mem: ValidReadMemIO)(f: UInt => UInt): Unit = {
      val cs = Util.between(addr, r)
      mem.rd := cs && readStrobe
      mem.addr := f(cpu.addr)
      when(cs && cpu.rw && mem.valid) {
        cpu.din := mem.dout
        cpu.dtack := true.B
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
      val cs = Util.between(addr, r)
      mem.wr := cs && (upperWriteStrobe || lowerWriteStrobe)
      mem.addr := f(cpu.addr)
      mem.mask := cpu.uds ## cpu.lds
      mem.din := cpu.dout
      when(cs && !cpu.rw) {
        cpu.dtack := true.B
      }
    }

    /**
     * Maps an address range to the given getter and setter functions.
     *
     * @param f The getter function.
     * @param g The setter function.
     */
    def rw(f: (UInt, UInt) => UInt)(g: (UInt, UInt, UInt) => Unit): Unit = {
      val cs = Util.between(addr, r)
      val offset = addr - r.start.U
      when(cs) {
        when(cpu.rw) {
          cpu.din := f(cpu.addr, offset)
        }.otherwise {
          g(cpu.addr, offset, cpu.dout)
        }
        cpu.dtack := true.B
      }
    }

    /**
     * Maps an address range to the given getter function.
     *
     * @param f The getter function.
     */
    def r(f: (UInt, UInt) => UInt): Unit = {
      val cs = Util.between(addr, r)
      val offset = addr - r.start.U
      when(cs && cpu.rw) {
        cpu.din := f(cpu.addr, offset)
        cpu.dtack := true.B
      }
    }

    /**
     * Maps an address range to the given setter function.
     *
     * @param f The setter function.
     */
    def w(f: (UInt, UInt, UInt) => Unit): Unit = {
      val cs = Util.between(addr, r)
      val offset = addr - r.start.U
      when(cs && !cpu.rw) {
        f(cpu.addr, offset, cpu.dout)
        cpu.dtack := true.B
      }
    }

    /** Ignores the address range. Read/write operations will still be acknowledged. */
    def ignore(): Unit = {
      rw((_, _) => 0.U)((_, _, _) => {})
    }
  }
}
