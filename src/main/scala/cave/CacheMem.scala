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

package cave

import cave.types.ValidReadMemIO
import chisel3._

/**
 * A direct-mapped cache memory.
 *
 * @param inAddrWidth The width of the input address bus.
 * @param inDataWidth The width of the input data bus.
 * @param outAddrWidth The width of the output address bus.
 * @param outDataWidth The width of the output data bus.
 */
class CacheMem(inAddrWidth: Int, inDataWidth: Int, outAddrWidth: Int, outDataWidth: Int) extends Module {
  val io = IO(new Bundle {
    /** Input port */
    val in = Flipped(ValidReadMemIO(inAddrWidth, inDataWidth))
    /** Output port */
    val out = ValidReadMemIO(outAddrWidth, outDataWidth)
  })

  class CacheMemBlackBox extends BlackBox {
    val io = IO(new Bundle {
      val rst_i = Input(Reset())
      val clk_i = Input(Clock())
      val agent_to_cache_addr_i = Input(UInt(inAddrWidth.W))
      val agent_to_cache_read_i = Input(Bool())
      val cache_to_agent_data_o = Output(Bits(inDataWidth.W))
      val cache_to_agent_valid_o = Output(Bool())
      val cache_to_memory_addr_o = Output(UInt(outAddrWidth.W))
      val cache_to_memory_read_o = Output(Bool())
      val memory_to_cache_data_i = Input(Bits(outDataWidth.W))
      val memory_to_cache_valid_i = Input(Bool())
    })

    override def desiredName = "cache_memory"
  }

  val cache = Module(new CacheMemBlackBox)
  cache.io.rst_i := reset
  cache.io.clk_i := clock

  cache.io.agent_to_cache_addr_i := io.in.addr
  cache.io.agent_to_cache_read_i := io.in.rd
  io.in.dout := cache.io.cache_to_agent_data_o
  io.in.valid := cache.io.cache_to_agent_valid_o

  io.out.addr := cache.io.cache_to_memory_addr_o
  io.out.rd := cache.io.cache_to_memory_read_o
  cache.io.memory_to_cache_data_i := io.out.dout
  cache.io.memory_to_cache_valid_i := io.out.valid
}
