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

package arcadia.gfx

import arcadia.{SVec2, UVec2}
import arcadia.util.Counter
import chisel3._

/**
 * Represents the video timing configuration.
 *
 * @param clockFreq The video clock frequency (Hz).
 * @param clockDiv  The video clock divider.
 * @param hFreq     The horizontal frequency (Hz).
 * @param vFreq     The vertical frequency (Hz).
 * @param hInit     The initial horizontal position (for testing).
 * @param vInit     The initial vertical position (for testing).
 */
case class VideoTimingConfig(clockFreq: Double,
                             clockDiv: Int,
                             hFreq: Double,
                             vFreq: Double,
                             hInit: Int = 0,
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
    /** Display region */
    val display = Input(UVec2(9.W))
    /** Front porch region */
    val frontPorch = Input(UVec2(9.W))
    /** Retrace region */
    val retrace = Input(UVec2(9.W))
    /** CRT offset */
    val offset = Input(SVec2(4.W))
    /** Timing port */
    val timing = VideoTimingIO()
  })

  // Counters
  val (_, clockDivWrap) = Counter.static(config.clockDiv)
  val (x, xWrap) = Counter.static(config.width, enable = clockDivWrap, init = config.hInit)
  val (y, yWrap) = Counter.static(config.height, enable = clockDivWrap && xWrap, init = config.vInit)

  // Horizontal regions
  val hBeginDisplay = (config.width.S + io.offset.x).asUInt - io.display.x - io.frontPorch.x - io.retrace.x
  val hEndDisplay = (config.width.S + io.offset.x).asUInt - io.frontPorch.x - io.retrace.x
  val hBeginSync = config.width.U - io.retrace.x
  val hEndSync = config.width.U

  // Vertical regions
  val vBeginDisplay = (config.height.S + io.offset.y).asUInt - io.display.y - io.frontPorch.y - io.retrace.y
  val vEndDisplay = (config.height.S + io.offset.y).asUInt - io.frontPorch.y - io.retrace.y
  val vBeginSync = config.height.U - io.retrace.y
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
