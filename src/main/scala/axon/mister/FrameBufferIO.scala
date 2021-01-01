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

package axon.mister

import chisel3._

/** MiSTer frame buffer IO */
class FrameBufferIO extends Bundle {
  /** Asserted when the frame buffer is enabled */
  val enable = Output(Bool())
  /** The horizontal size of the frame buffer */
  val hSize = Output(UInt(12.W))
  /** The vertical size of the frame buffer */
  val vSize = Output(UInt(12.W))
  /** The frame buffer format */
  val format = Output(Bits(5.W))
  /** The base address value */
  val base = Output(UInt(32.W))
  /** The horizontal width of the frame buffer in bytes */
  val stride = Output(UInt(14.W))
  /** Vertical blank */
  val vBlank = Input(Bool())
  /** Low latency flag */
  val lowLat = Input(Bool())
  /** Force blank flag */
  val forceBlank = Output(Bool())
}

object FrameBufferIO {
  /** 8 bits per pixel */
  val FORMAT_8BPP = 0x3
  /** 16 bits per pixel */
  val FORMAT_16BPP = 0x4
  /** 24 bits per pixel */
  val FORMAT_24BPP = 0x5
  /** 32 bits per pixel */
  val FORMAT_32BPP = 0x6
  /** BGR pixel format */
  val FORMAT_BGR = 0x10
}
