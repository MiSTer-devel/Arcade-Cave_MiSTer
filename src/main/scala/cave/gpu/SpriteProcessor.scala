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
 * @param maxSprites The maximum number of sprites to render.
 */
class SpriteProcessor(maxSprites: Int = 1024) extends Module {
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
    /** Debug port */
    val debug = Output(new Bundle {
      val idle = Bool()
      val latch = Bool()
      val check = Bool()
      val ready = Bool()
      val pending = Bool()
      val next = Bool()
      val done = Bool()
    })
  })

  // States
  object State {
    val idle :: latch :: check :: ready :: pending :: next :: done :: Nil = Enum(7)
  }

  // Set 8BPP flag
  val is8BPP = io.gameConfig.spriteFormat === GameConfig.GFX_FORMAT_SPRITE_8BPP.U

  // Decode the sprite
  val sprite = Sprite.decode(io.spriteRam.dout, io.gameConfig.spriteZoom)

  // Wires
  val effectiveRead = Wire(Bool())

  // Registers
  val stateReg = RegInit(State.idle)
  val spriteReg = RegEnable(sprite, stateReg === State.latch)
  val numTilesReg = RegEnable(spriteReg.cols * spriteReg.rows, stateReg === State.check)
  val burstPendingReg = RegInit(false.B)

  // The FIFO buffers pixel data for up to two 16x16x8BPP tiles, or four 16x16x4BPP tiles. It is
  // configured in show-ahead mode, which means there is valid output as soon as an element has been
  // written to the queue. A read request will move to the next element in the queue.
  val fifo = Module(new Queue(Bits(Config.TILE_ROM_DATA_WIDTH.W), 64, flow = true))

  // Counters
  val (spriteCounter, spriteCounterWrap) = Counter.static(maxSprites, stateReg === State.next)
  val (tileCounter, tileCounterWrap) = Counter.dynamic(numTilesReg, effectiveRead)

  // Sprite blitter
  val spriteBlitter = Module(new SpriteBlitter)
  spriteBlitter.io.gameConfig <> io.gameConfig
  spriteBlitter.io.options <> io.options
  spriteBlitter.io.paletteRam <> io.paletteRam
  spriteBlitter.io.priority <> io.priority
  spriteBlitter.io.frameBuffer <> io.frameBuffer

  // Tile decoder
  val tileDecoder = Module(new LargeTileDecoder)
  tileDecoder.io.format := io.gameConfig.spriteFormat
  tileDecoder.io.rom <> fifo.io.deq
  tileDecoder.io.pixelData <> spriteBlitter.io.pixelData

  // Set done flag
  val done = stateReg === State.done && !spriteBlitter.io.busy

  // Set tile ROM read flag
  val tileRomRead = stateReg === State.pending && !burstPendingReg && fifo.io.count <= 32.U

  // Set effective read flag
  effectiveRead := tileRomRead && !io.tileRom.waitReq

  // Set sprite RAM address
  val spriteRamAddr = io.spriteBank ## spriteCounter

  // Set tile ROM address
  val tileRomAddr = (spriteReg.code + tileCounter) << Mux(is8BPP, 8.U, 7.U)

  // Set tile ROM burst length
  val tileRomBurstLength = Mux(is8BPP, 32.U, 16.U)

  // Toggle burst pending register
  when(stateReg === State.idle) {
    burstPendingReg := false.B
  }.elsewhen(effectiveRead) {
    burstPendingReg := true.B
  }.elsewhen(io.tileRom.burstDone) {
    burstPendingReg := false.B
  }

  // Enqueue a sprite in the blitter when the processor is ready
  when(stateReg === State.ready) {
    spriteBlitter.io.sprite.enq(spriteReg)
  } otherwise {
    spriteBlitter.io.sprite.noenq()
  }

  // Enqueue tile ROM data in the FIFO when it is available
  when(io.tileRom.valid) {
    fifo.io.enq.enq(io.tileRom.dout)
  } otherwise {
    fifo.io.enq.noenq()
  }

  // FSM
  switch(stateReg) {
    // Wait for the start signal
    is(State.idle) {
      when(io.start) { stateReg := State.latch }
    }

    // Latch the sprite
    is(State.latch) { stateReg := State.check }

    // Check whether the sprite is enabled
    is(State.check) {
      stateReg := Mux(spriteReg.isEnabled, State.ready, State.next)
    }

    // Wait for the blitter to be ready
    is(State.ready) {
      when(spriteBlitter.io.sprite.ready) { stateReg := State.pending }
    }

    // Wait for all sprite tiles to load
    is(State.pending) {
      when(tileCounterWrap) { stateReg := State.next }
    }

    // Increment the sprite counter
    is(State.next) {
      stateReg := Mux(spriteCounterWrap, State.done, State.latch)
    }

    // Wait for the blitter to finish
    is(State.done) {
      when(!spriteBlitter.io.busy) { stateReg := State.idle }
    }
  }

  // Outputs
  io.done := done
  io.spriteRam.rd := true.B
  io.spriteRam.addr := spriteRamAddr
  io.tileRom.rd := tileRomRead
  io.tileRom.addr := tileRomAddr
  io.tileRom.burstLength := tileRomBurstLength
  io.debug.idle := stateReg === State.idle
  io.debug.latch := stateReg === State.latch
  io.debug.check := stateReg === State.check
  io.debug.ready := stateReg === State.ready
  io.debug.pending := stateReg === State.ready
  io.debug.next := stateReg === State.next
  io.debug.done := stateReg === State.done

  printf(p"SpriteProcessor(state: $stateReg, spriteCounter: $spriteCounter ($spriteCounterWrap), done: $done)\n")
}
