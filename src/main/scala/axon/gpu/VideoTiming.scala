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

package axon.gpu

import axon.types.Vec2
import axon.util.Counter
import chisel3._

/** Represents the video timing signals. */
class VideoIO extends Bundle {
  /** The enable signal is asserted when the beam is in the display region. */
  val enable = Output(Bool())
  /** Position */
  val pos = Output(new Vec2)
  /** The horizontal sync signal. */
  val hSync = Output(Bool())
  /** The vertical sync signal. */
  val vSync = Output(Bool())
  /** The horizontal blanking signal. */
  val hBlank = Output(Bool())
  /** The vertical blanking signal. */
  val vBlank = Output(Bool())
}

/**
 * Represents the video timing configuration.
 *
 * @param hDisplay    The horizontal width.
 * @param hFrontPorch The width of the horizontal front porch region.
 * @param hRetrace    The width of the horizontal retrace region.
 * @param hBackPorch  The width of the horizontal front porch region.
 * @param hOffset     The horizontal offset.
 * @param hInit       The initial horizontal position (for testing).
 * @param vDisplay    The vertical height.
 * @param vFrontPorch The width of the vertical front porch region.
 * @param vRetrace    The width of the vertical retrace region.
 * @param vBackPorch  The width of the vertical front porch region.
 * @param vOffset     The vertical offset.
 * @param vInit       The initial vertical position (for testing).
 */
case class VideoTimingConfig(hDisplay: Int,
                             hFrontPorch: Int,
                             hRetrace: Int,
                             hBackPorch: Int,
                             hOffset: Int = 0,
                             hInit: Int = 0,
                             vDisplay: Int,
                             vFrontPorch: Int,
                             vRetrace: Int,
                             vBackPorch: Int,
                             vOffset: Int = 0,
                             vInit: Int = 0) {
  // Horizontal
  val hBeginSync = hBackPorch + hDisplay + hFrontPorch
  val hEndSync = hBackPorch + hDisplay + hFrontPorch + hRetrace
  val hBeginDisplay = hBackPorch
  val hEndDisplay = hBackPorch + hDisplay

  // Vertical
  val vBeginSync = vBackPorch + vDisplay + vFrontPorch
  val vEndSync = vBackPorch + vDisplay + vFrontPorch + vRetrace
  val vBeginDisplay = vBackPorch
  val vEndDisplay = vBackPorch + vDisplay
}

/**
 * Generates the video timing signals required for driving a 15kHz CRT.
 *
 * The horizontal sync signal tells the CRT when to start a new scanline, and the vertical sync
 * signal tells it when to start a new field.
 *
 * The blanking signals indicate whether the beam is in either the horizontal or vertical blanking
 * regions. Video output is disabled while the beam is in these regions.
 *
 * @param config The video timing configuration.
 */
class VideoTiming(config: VideoTimingConfig) extends Module {
  val io = IO(new VideoIO)

  // Counters
  val (x, xWrap) = Counter.static(config.hEndSync, init = config.hInit)
  val (y, yWrap) = Counter.static(config.vEndSync, enable = xWrap, init = config.vInit)

  // Adjust the position so the display region begins at the origin
  val pos = Vec2(x - config.hOffset.U, y - config.vOffset.U)

  // Sync signals
  val hSync = x >= config.hBeginSync.U && x < config.hEndSync.U
  val vSync = y >= config.vBeginSync.U && y < config.vEndSync.U

  // Display signals
  val hDisplay = x >= config.hBeginDisplay.U && x < config.hEndDisplay.U
  val vDisplay = y >= config.vBeginDisplay.U && y < config.vEndDisplay.U

  // Outputs
  io.pos := pos
  io.hSync := hSync
  io.vSync := vSync
  io.hBlank := !hDisplay
  io.vBlank := !vDisplay
  io.enable := hDisplay & vDisplay
}
