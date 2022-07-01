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

package arcadia.mem.request

import arcadia.mem.WriteMemIO
import chisel3._

/** Represents a memory write request. */
class WriteRequest[S <: Data, T <: Data](s: S, t: T) extends Bundle {
  /** Write enable */
  val wr = Output(Bool())
  /** Address bus */
  val addr = Output(s)
  /** Data bus */
  val din = Output(t)
  /** Byte mask */
  val mask = Output(UInt((t.getWidth / 8).W))
}

object WriteRequest {
  /**
   * Creates a write request.
   *
   * @param wr   Write enable.
   * @param addr The memory address.
   * @param din  The data value.
   * @param mask The byte mask.
   */
  def apply[S <: Data, T <: Data](wr: Bool, addr: S, din: T, mask: UInt): WriteRequest[S, T] = {
    val req = Wire(new WriteRequest(chiselTypeOf(addr), chiselTypeOf(din)))
    req.wr := wr
    req.addr := addr
    req.din := din
    req.mask := mask
    req
  }

  /**
   * Creates a write request from a write interface.
   *
   * @param mem The write interface.
   * @return A request bundle.
   */
  def apply(mem: WriteMemIO): WriteRequest[UInt, Bits] = {
    val req = Wire(new WriteRequest[UInt, Bits](UInt(mem.addrWidth.W), Bits(mem.dataWidth.W)))
    req.wr := mem.wr
    req.addr := mem.addr
    req.din := mem.din
    req.mask := mem.mask
    req
  }
}
