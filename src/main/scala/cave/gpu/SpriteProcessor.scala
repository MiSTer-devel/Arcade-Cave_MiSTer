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
import axon.types._
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
    /** When the start flag is asserted, the sprites are rendered to the frame buffer */
    val start = Input(Bool())
    /** The busy flag is asserted while the processor is rendering */
    val busy = Output(Bool())
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
      val load = Bool()
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
    val idle :: load :: latch :: check :: ready :: pending :: next :: done :: Nil = Enum(8)
  }

  // Set 8BPP flag
  val is8BPP = io.gameConfig.spriteFormat === Config.GFX_FORMAT_8BPP.U

  // Decode the sprite
  val sprite = Sprite.decode(io.spriteRam.dout, io.gameConfig.spriteZoom)

  // Wires
  val effectiveRead = Wire(Bool())

  // Registers
  val stateReg = RegInit(State.idle)
  val spriteReg = RegEnable(sprite, stateReg === State.latch)
  val numTilesReg = RegEnable(spriteReg.cols * spriteReg.rows, stateReg === State.check)
  val burstPendingReg = RegInit(false.B)

  // The FIFO is used to buffer the raw data read from the tile ROM.
  //
  // The queue is configured in show-ahead mode, which means there will be valid output as soon as
  // an element has been written to the queue.
  val fifo = Module(new Queue(Bits(Config.TILE_ROM_DATA_WIDTH.W), Config.FIFO_DEPTH, flow = true))

  // Counters
  val (spriteCounter, spriteCounterWrap) = Counter.static(maxSprites, stateReg === State.next)
  val (tileCounter, tileCounterWrap) = Counter.dynamic(numTilesReg, effectiveRead)

  // Sprite blitter
  val blitter = Module(new SpriteBlitter)
  blitter.io.paletteRam <> io.paletteRam
  blitter.io.priority <> io.priority
  blitter.io.frameBuffer <> io.frameBuffer

  // Sprite decoder
  val decoder = Module(new SpriteDecoder)
  decoder.io.format := io.gameConfig.spriteFormat
  decoder.io.rom <> fifo.io.deq
  decoder.io.pixelData <> blitter.io.pixelData

  // Set tile ROM read flag
  val tileRomRead = stateReg === State.pending && !burstPendingReg && fifo.io.count <= (Config.FIFO_DEPTH / 2).U

  // Set effective read flag
  effectiveRead := tileRomRead && !io.tileRom.waitReq

  // Set sprite RAM address
  val spriteRamAddr = io.spriteBank ## spriteCounter

  // Set tile ROM address
  val tileRomAddr = (spriteReg.code + tileCounter) << Mux(is8BPP, 8.U, 7.U)

  // Set tile ROM burst length
  val tileRomBurstLength = Mux(is8BPP, 32.U, 16.U)

  // The burst pending register is asserted when there is a burst in progress
  when(effectiveRead) {
    burstPendingReg := true.B
  }.elsewhen(io.tileRom.burstDone) {
    burstPendingReg := false.B
  }

  // Enqueue the blitter configuration when the blitter is ready
  when(stateReg === State.ready) {
    val config = Wire(new SpriteBlitterConfig)
    config.sprite := spriteReg
    config.numColors := io.gameConfig.numColors
    config.rotate := io.options.rotate
    config.flip := io.options.flip
    blitter.io.config.enq(config)
  } otherwise {
    blitter.io.config.noenq()
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
      when(io.start) { stateReg := State.load }
    }

    // Load the sprite
    is(State.load) { stateReg := State.latch }

    // Latch the sprite
    is(State.latch) { stateReg := State.check }

    // Check whether the sprite is enabled
    is(State.check) { stateReg := Mux(spriteReg.isEnabled, State.ready, State.next) }

    // Wait until the blitter is ready
    is(State.ready) {
      when(blitter.io.config.ready) { stateReg := State.pending }
    }

    // Wait until the tiles have been loaded into the FIFO
    is(State.pending) {
      when(tileCounterWrap) { stateReg := State.next }
    }

    // Increment the sprite counter
    is(State.next) {
      stateReg := Mux(spriteCounterWrap, State.done, State.load)
    }

    // Wait until the blitter has finished
    is(State.done) {
      when(!blitter.io.busy) { stateReg := State.idle }
    }
  }

  // Outputs
  io.busy := stateReg =/= State.idle
  io.spriteRam.rd := stateReg === State.load
  io.spriteRam.addr := spriteRamAddr
  io.tileRom.rd := tileRomRead
  io.tileRom.addr := tileRomAddr
  io.tileRom.burstLength := tileRomBurstLength
  io.debug.idle := stateReg === State.idle
  io.debug.load := stateReg === State.load
  io.debug.latch := stateReg === State.latch
  io.debug.check := stateReg === State.check
  io.debug.ready := stateReg === State.ready
  io.debug.pending := stateReg === State.ready
  io.debug.next := stateReg === State.next
  io.debug.done := stateReg === State.done

  printf(p"SpriteProcessor(state: $stateReg, spriteCounter: $spriteCounter ($spriteCounterWrap))\n")
}
