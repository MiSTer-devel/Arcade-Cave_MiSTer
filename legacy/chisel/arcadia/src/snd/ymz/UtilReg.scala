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

package arcadia.snd.ymz

import chisel3._
import chisel3.util._

/** Represents the utility register. */
class UtilReg extends Bundle {
  /** IRQ mask */
  val irqMask = Bits(8.W)
  /** Flags */
  val flags = new Bundle {
    /** Key on enable */
    val keyOnEnable = Bool()
    /** Memory enable */
    val memEnable = Bool()
    /** IRQ enable */
    val irqEnable = Bool()
  }
}

object UtilReg {
  /**
   * Decodes a utility register from the given register file.
   *
   * @param registerFile The register file.
   */
  def fromRegisterFile(registerFile: Vec[UInt]): UtilReg = {
    Cat(
      registerFile(0xfe), // IRQ mask
      registerFile(0xff)(7), // key on enable
      registerFile(0xff)(6), // memory enable
      registerFile(0xff)(4), // IRQ enable
    ).asTypeOf(new UtilReg)
  }
}
