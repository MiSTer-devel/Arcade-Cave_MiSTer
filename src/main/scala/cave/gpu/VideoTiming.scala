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

package cave.gpu

import chisel3._
import chisel3.util._

/**
 * Represents the video timing signals.
 */
class Video extends Bundle {
  /** The horizontal position of the beam. */
  val x = UInt(9.W)
  /** The vertical position of the beam. */
  val y = UInt(9.W)
  /** The horizontal sync signal. */
  val hsync = Bool()
  /** The vertical sync signal. */
  val vsync = Bool()
  /** The horizontal blanking signal. */
  val hblank = Bool()
  /** The vertical blanking signal. */
  val vblank = Bool()
  /** The enable signal is asserted when the beam is in the display region. */
  val enable = Bool()
}

/**
 * Represents the video timing configuration.
 */
case class VideoTimingConfig(hDisplay: Int = 256,
                             hFrontPorch: Int = 40,
                             hRetrace: Int = 32,
                             hBackPorch: Int = 56,
                             vDisplay: Int = 224,
                             vFrontPorch: Int = 16,
                             vRetrace: Int = 8,
                             vBackPorch: Int = 16) {
  val hBeginScan    = 0
  val hEndScan      = hBackPorch+hDisplay+hFrontPorch+hRetrace // 384
  val hBeginSync    = hBackPorch+hDisplay+hFrontPorch
  val hEndSync      = hBackPorch+hDisplay+hFrontPorch+hRetrace
  val hBeginDisplay = hBackPorch
  val hEndDisplay   = hBackPorch+hDisplay

  val vBeginScan    = 0
  val vEndScan      = vBackPorch+vDisplay+vFrontPorch+vRetrace // 264
  val vBeginSync    = vBackPorch+vDisplay+vFrontPorch
  val vEndSync      = vBackPorch+vDisplay+vFrontPorch+vRetrace
  val vBeginDisplay = vBackPorch
  val vEndDisplay   = vBackPorch+vDisplay
}

/**
 * This module generates the video timing signals required for driving a
 * 15kHz CRT.
 *
 * The horizontal sync signal tells the CRT when to start a new scanline, and
 * the vertical sync signal tells it when to start a new field.
 *
 * The blanking signals indicate whether the beam is in either the horizontal or
 * vertical blanking regions. Video output is disabled while the beam is in
 * these regions.
 *
 * @param config The video timing configuration.
 * @param hStart The horizontal start position (used for testing).
 * @param vStart The vertical start position (used for testing).
 */
class VideoTiming(config: VideoTimingConfig, hStart: Int = 0, vStart: Int = 0) extends Module {
  val io = IO(new Bundle {
    /** Clock enable */
    val cen = Input(Bool())
    /** Video timing signals */
    val video = Output(new Video)
  })

  // Position registers
  val x = RegInit(hStart.U(9.W))
  val y = RegInit(vStart.U(9.W))

  // Counter control signals
  val xStep = io.cen
  val yStep = io.cen && x === (config.hBeginSync-1).U
  val xWrap = x === (config.hEndScan-1).U
  val yWrap = y === (config.vEndScan-1).U

  // Horizontal counter
  when(xStep) {
    x := x + 1.U
    when(xWrap) { x := config.hBeginScan.U }
  }

  // Vertical counter
  when(yStep) {
    y := y + 1.U
    when(yWrap) { y := config.vBeginScan.U }
  }

  // Sync signals
  val hsync = x >= config.hBeginSync.U && x < config.hEndSync.U
  val vsync = y >= config.vBeginSync.U && y < config.vEndSync.U

  // Blanking signals
  val hblank = !(x >= config.hBeginDisplay.U && x < config.hEndDisplay.U)
  val vblank = !(y >= config.vBeginDisplay.U && y < config.vEndDisplay.U)

  // Outputs
  io.video.x := x
  io.video.y := y
  io.video.hsync := hsync
  io.video.vsync := vsync
  io.video.hblank := hblank
  io.video.vblank := vblank
  io.video.enable := !(hblank | vblank)
}
