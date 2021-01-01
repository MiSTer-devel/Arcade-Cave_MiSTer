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

package axon.gpu

import axon.types.{SVec2, Vec2}
import axon.util.Counter
import chisel3._

/** Represents the video timing signals. */
class VideoIO extends Bundle {
  /** The enable signal is asserted when the beam is in the display region. */
  val enable = Output(Bool())
  /** Position */
  val pos = Output(new Vec2(9))
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
 * @param clockFreq   The pixel clock frequency (Hz).
 * @param hFreq       The horizontal frequency (Hz).
 * @param hDisplay    The horizontal display width.
 * @param hFrontPorch The width of the horizontal front porch region.
 * @param hRetrace    The width of the horizontal retrace region.
 * @param hOffset     The horizontal offset.
 * @param hInit       The initial horizontal position (for testing).
 * @param vFreq       The vertical frequency (Hz).
 * @param vDisplay    The vertical display height.
 * @param vFrontPorch The width of the vertical front porch region.
 * @param vRetrace    The width of the vertical retrace region.
 * @param vOffset     The vertical offset.
 * @param vInit       The initial vertical position (for testing).
 */
case class VideoTimingConfig(clockFreq: Double,
                             hFreq: Double,
                             hDisplay: Int,
                             hFrontPorch: Int,
                             hRetrace: Int,
                             hOffset: Int = 0,
                             hInit: Int = 0,
                             vFreq: Double,
                             vDisplay: Int,
                             vFrontPorch: Int,
                             vRetrace: Int,
                             vOffset: Int = 0,
                             vInit: Int = 0) {
  /** Total width in pixels */
  val width = math.ceil(clockFreq / hFreq).toInt
  /** Total height in pixels */
  val height = math.ceil(hFreq / vFreq).toInt
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
  val io = IO(new Bundle {
    /** CRT offset */
    val offset = Input(new SVec2(Config.SCREEN_OFFSET_WIDTH))
    /** Video port */
    val video = Output(new VideoIO)
  })

  // Counters
  val (x, xWrap) = Counter.static(config.width, init = config.hInit)
  val (y, yWrap) = Counter.static(config.height, enable = xWrap, init = config.vInit)

  // Horizontal regions
  val hBeginDisplay = (config.width.S - config.hDisplay.S - config.hFrontPorch.S - config.hRetrace.S + io.offset.x).asUInt
  val hEndDisplay = (config.width.S - config.hFrontPorch.S - config.hRetrace.S + io.offset.x).asUInt
  val hBeginSync = config.width.U - config.hRetrace.U
  val hEndSync = config.width.U

  // Vertical regions
  val vBeginDisplay = (config.height.S - config.vDisplay.S - config.vFrontPorch.S - config.vRetrace.S + io.offset.y).asUInt
  val vEndDisplay = (config.height.S - config.vFrontPorch.S - config.vRetrace.S + io.offset.y).asUInt
  val vBeginSync = config.height.U - config.vRetrace.U
  val vEndSync = config.height.U

  // Offset the position so the display region begins at the origin
  val pos = Vec2(x - hBeginDisplay, y - vBeginDisplay)

  // Sync signals
  val hSync = x >= hBeginSync && x < hEndSync
  val vSync = y >= vBeginSync && y < vEndSync

  // Blanking signals
  val hDisplay = x >= hBeginDisplay && x < hEndDisplay
  val vDisplay = y >= vBeginDisplay && y < vEndDisplay

  // Outputs
  io.video.pos := pos
  io.video.hSync := hSync
  io.video.vSync := vSync
  io.video.hBlank := !hDisplay
  io.video.vBlank := !vDisplay
  io.video.enable := hDisplay & vDisplay
}
