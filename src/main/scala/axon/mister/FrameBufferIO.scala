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
 * Copyright (c) 2021 Josh Bassett
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

package axon.mister

import chisel3._

/** MiSTer frame buffer IO */
class FrameBufferIO private extends Bundle {
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

  def apply() = new FrameBufferIO
}
