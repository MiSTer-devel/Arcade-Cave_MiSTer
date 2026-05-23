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

import chisel3._

/** Represents a memory request. */
class ReadWriteRequest[S <: Data, T <: Data](s: S, t: T) extends Bundle {
  /** Read enable */
  val rd = Output(Bool())
  /** Write enable */
  val wr = Output(Bool())
  /** Address bus */
  val addr = Output(s)
  /** Data bus */
  val din = Output(t)
  /** Byte mask */
  val mask = Output(UInt((t.getWidth / 8).W))
}

object ReadWriteRequest {
  /**
   * Creates a read-write request.
   *
   * @param rd   Read enable.
   * @param wr   Write enable.
   * @param addr The memory address.
   * @param din  The data value.
   * @param mask The byte mask.
   */
  def apply[S <: Data, T <: Data](rd: Bool, wr: Bool, addr: S, din: T, mask: UInt): ReadWriteRequest[S, T] = {
    val req = Wire(new ReadWriteRequest(chiselTypeOf(addr), chiselTypeOf(din)))
    req.rd := rd
    req.wr := wr
    req.addr := addr
    req.din := din
    req.mask := mask
    req
  }
}
