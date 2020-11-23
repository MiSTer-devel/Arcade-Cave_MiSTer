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

package cave.types

import axon.mem.ValidReadMemIO
import cave.Config
import chisel3._
import chisel3.util._

/** Tile ROM IO */
class TileRomIO extends ValidReadMemIO(Config.TILE_ROM_ADDR_WIDTH, Config.TILE_ROM_DATA_WIDTH) {
  /** Tiny burst flag */
  val tinyBurst = Output(Bool())
  /** Burst done flag */
  val burstDone = Input(Bool())

  override def cloneType: this.type = new TileRomIO().asInstanceOf[this.type]

  /**
   * Maps the address using the given function.
   *
   * @param f The transform function.
   */
  override def mapAddr(f: UInt => UInt): TileRomIO = {
    val mem = Wire(chiselTypeOf(this))
    mem.rd := this.rd
    mem.tinyBurst := this.tinyBurst
    mem.addr := f(this.addr)
    this.dout := mem.dout
    this.valid := mem.valid
    this.burstDone := mem.burstDone
    mem
  }
}

object TileRomIO {
  /**
   * Multiplexes requests from multiple write-only memory interface to a single write-only memory interfaces. The
   * request is routed to the first enabled interface.
   *
   * @param outs A list of enable-interface pairs.
   */
  def mux(outs: Seq[(Bool, TileRomIO)]): TileRomIO = {
    val mem = Wire(chiselTypeOf(outs.head._2))
    mem.tinyBurst := MuxCase(false.B, outs.map(a => a._1 -> a._2.tinyBurst))
    mem.rd := MuxCase(false.B, outs.map(a => a._1 -> a._2.rd))
    mem.addr := MuxCase(DontCare, outs.map(a => a._1 -> a._2.addr))
    outs.foreach { case (enable, out) =>
      out.dout := mem.dout
      out.valid := mem.valid && enable
      out.burstDone := mem.burstDone && enable
    }
    mem
  }
}
