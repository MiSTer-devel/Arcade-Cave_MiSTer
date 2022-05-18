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

package axon.gfx

import axon.types._
import axon.util.Counter
import chisel3._

/**
 * Represents the video timing configuration.
 *
 * @param clockFreq   The video clock frequency (Hz).
 * @param clockDiv    The video clock divider.
 * @param hFreq       The horizontal frequency (Hz).
 * @param hDisplay    The horizontal display width.
 * @param hFrontPorch The width of the horizontal front porch region.
 * @param hRetrace    The width of the horizontal retrace region.
 * @param hOffset     The horizontal offset (in pixels) of the beam position.
 * @param hInit       The initial horizontal position (for testing).
 * @param vFreq       The vertical frequency (Hz).
 * @param vDisplay    The vertical display height.
 * @param vFrontPorch The width of the vertical front porch region.
 * @param vRetrace    The width of the vertical retrace region.
 * @param vOffset     The vertical offset (in pixels) of the beam position.
 * @param vInit       The initial vertical position (for testing).
 */
case class VideoTimingConfig(clockFreq: Double,
                             clockDiv: Int,
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
  val width = math.round(clockFreq / clockDiv / hFreq).toInt
  /** Total height in pixels */
  val height = math.round(hFreq / vFreq).toInt
}

/**
 * Generates the analog video timing signals required for driving a 15kHz CRT.
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
    val offset = Input(SVec2(OptionsIO.SCREEN_OFFSET_WIDTH.W))
    /** Timing port */
    val timing = VideoTimingIO()
  })

  // Counters
  val (_, clockDivWrap) = Counter.static(config.clockDiv)
  val (x, xWrap) = Counter.static(config.width, enable = clockDivWrap, init = config.hInit)
  val (y, yWrap) = Counter.static(config.height, enable = clockDivWrap && xWrap, init = config.vInit)

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

  // Offset the position vector so the display region begins at the origin (i.e. (0, 0))
  val pos = UVec2(x - hBeginDisplay, y - vBeginDisplay)

  // Sync signals
  val hSync = x >= hBeginSync && x < hEndSync
  val vSync = y >= vBeginSync && y < vEndSync

  // Blanking signals
  val hBlank = x < hBeginDisplay || x >= hEndDisplay
  val vBlank = y < vBeginDisplay || y >= vEndDisplay

  // Outputs
  io.timing.clockEnable := clockDivWrap
  io.timing.displayEnable := !(hBlank || vBlank)
  io.timing.pos := pos
  io.timing.hSync := hSync
  io.timing.vSync := vSync
  io.timing.hBlank := hBlank
  io.timing.vBlank := vBlank

  printf(p"VideoTiming(pos: (${ pos.x }, ${ pos.y }), width: ${ config.width }, height: ${ config.height }), hBeginDisplay: $hBeginDisplay, vBeginDisplay: $vBeginDisplay, hSync: $hSync , vSync: $vSync \n")
}
