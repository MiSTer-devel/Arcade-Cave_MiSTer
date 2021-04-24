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

package cave.gpu

import axon.mem._
import axon.types.OptionsIO
import axon.util.Counter
import cave.Config
import cave.types._
import chisel3._
import chisel3.util._

/**
 * The sprite processor handles rendering sprites.
 *
 * @param numSprites The maximum number of sprites to render.
 */
class SpriteProcessor(numSprites: Int = 1024) extends Module {
  val io = IO(new Bundle {
    /** Game config port */
    val gameConfig = Input(GameConfig())
    /** Options port */
    val options = OptionsIO()
    /** Start flag */
    val start = Input(Bool())
    /** Done flag */
    val done = Output(Bool())
    /** Sprite bank */
    val spriteBank = Input(Bool())
    /** Sprite RAM port */
    val spriteRam = ReadMemIO(Config.SPRITE_RAM_GPU_ADDR_WIDTH, Config.SPRITE_RAM_GPU_DATA_WIDTH)
    /** Palette RAM port */
    val paletteRam = ReadMemIO(Config.PALETTE_RAM_GPU_ADDR_WIDTH, Config.PALETTE_RAM_GPU_DATA_WIDTH)
    /** Tile ROM port */
    val tileRom = new TileRomIO
    /** Priority port */
    val priority = new PriorityIO
    /** Frame buffer port */
    val frameBuffer = WriteMemIO(Config.FRAME_BUFFER_ADDR_WIDTH, Config.FRAME_BUFFER_DATA_WIDTH)
  })

  // States
  object State {
    val idle :: check :: working :: Nil = Enum(3)
  }

  // Decode sprite info
  val spriteInfo = Sprite.decode(io.spriteRam.dout, io.gameConfig.spriteZoom)

  // Wires
  val effectiveRead = Wire(Bool())
  val spriteCounterEnable = Wire(Bool())
  val updateSpriteInfo = Wire(Bool())
  val pipelineReady = Wire(Bool())
  val pipelineDone = Wire(Bool())

  // Registers
  val stateReg = RegInit(State.idle)
  val spriteInfoReg = RegEnable(spriteInfo, updateSpriteInfo)
  val spriteInfoTaken = RegInit(false.B)
  val burstPendingReg = RegInit(false.B)
  val burstReady = RegInit(false.B)
  val burstCounterMax = RegEnable(spriteInfo.cols * spriteInfo.rows, updateSpriteInfo)

  // Tile FIFO
  val tileFifo = Module(new TileFIFO)

  // Counters
  val (spriteCounter, _) = Counter.static(numSprites * 2, // FIXME
    enable = spriteCounterEnable,
    reset = stateReg === State.idle
  )
  val (spriteSentCounter, _) = Counter.static(numSprites,
    enable = updateSpriteInfo,
    reset = stateReg === State.idle
  )
  val (spriteDoneCounter, _) = Counter.static(numSprites,
    enable = pipelineDone,
    reset = stateReg === State.idle
  )
  val (burstCounter, burstCounterWrap) = Counter.dynamic(burstCounterMax,
    enable = effectiveRead,
    reset = updateSpriteInfo
  )

  // Sprite blitter
  val spriteBlitter = Module(new SpriteBlitter)
  spriteBlitter.io.gameConfig <> io.gameConfig
  spriteBlitter.io.options <> io.options
  spriteBlitter.io.spriteInfo.bits := spriteInfoReg
  pipelineReady := spriteBlitter.io.spriteInfo.ready
  spriteBlitter.io.spriteInfo.valid := updateSpriteInfo
  spriteBlitter.io.pixelData <> tileFifo.io.deq
  spriteBlitter.io.paletteRam <> io.paletteRam
  spriteBlitter.io.priority <> io.priority
  spriteBlitter.io.frameBuffer <> io.frameBuffer
  pipelineDone := spriteBlitter.io.done

  // Set next sprite info flag
  spriteCounterEnable := stateReg === State.working &&
                         !spriteCounter(10) &&
                         (!spriteInfo.isEnabled || updateSpriteInfo)

  // Set update sprite info flag
  updateSpriteInfo := stateReg === State.working &&
                      !spriteCounter(10) &&
                      spriteInfo.isEnabled &&
                      spriteInfoTaken &&
                      !burstPendingReg

  // Set done flag
  val workDone = spriteCounter(10) && spriteSentCounter === spriteDoneCounter

  // Set burst done flag
  val doneBursting = burstPendingReg && burstCounterWrap

  // Set tile ROM read flag
  val tileRomRead = burstPendingReg && burstReady && tileFifo.io.enq.ready

  // Set effective read flag
  effectiveRead := tileRomRead && !io.tileRom.waitReq

  // Set sprite RAM address
  val spriteRamAddr = io.spriteBank ## spriteCounter(9, 0)

  // Set tile ROM address
  val tileRomAddr = (spriteInfoReg.code + burstCounter) * Config.LARGE_TILE_BYTE_SIZE.U

  // Enqueue valid tile ROM data into the tile FIFO
  when(io.tileRom.valid) {
    tileFifo.io.enq.enq(io.tileRom.dout)
  } otherwise {
    tileFifo.io.enq.noenq()
  }

  // Toggle sprite info taken register
  when(stateReg === State.idle) {
    spriteInfoTaken := true.B
  }.elsewhen(pipelineReady) {
    spriteInfoTaken := true.B
  }.elsewhen(updateSpriteInfo) {
    spriteInfoTaken := false.B
  }

  // Toggle burst pending register
  when(stateReg === State.idle) {
    burstPendingReg := false.B
  }.elsewhen(updateSpriteInfo) {
    burstPendingReg := true.B
  }.elsewhen(doneBursting) {
    burstPendingReg := false.B
  }

  // Toggle burst ready register
  when(stateReg === State.idle) {
    burstReady := true.B
  }.elsewhen(effectiveRead) {
    burstReady := false.B
  }.elsewhen(io.tileRom.burstDone) {
    burstReady := true.B
  }

  // FSM
  switch(stateReg) {
    // Wait for the start signal
    is(State.idle) {
      when(io.start) { stateReg := State.check }
    }

    // Check whether the sprite is enabled
    is(State.check) { stateReg := State.working }

    // Blit the sprite
    is(State.working) {
      when(workDone) { stateReg := State.idle }
    }
  }

  // Outputs
  io.done := workDone
  io.spriteRam.rd := true.B
  io.spriteRam.addr := spriteRamAddr
  io.tileRom.rd := tileRomRead
  io.tileRom.addr := tileRomAddr
  io.tileRom.burstLength := 16.U
}
