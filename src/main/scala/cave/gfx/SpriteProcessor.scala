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

package cave.gfx

import axon.util.Counter
import cave.Config
import cave.types._
import chisel3._
import chisel3.util._

/**
 * The sprite processor handles rendering sprites.
 *
 * @param maxSprites       The maximum number of sprites to render.
 * @param clearFrameBuffer Asserted when the frame buffer should be cleared before the sprites are
 *                         rendered.
 */
class SpriteProcessor(maxSprites: Int = 1024, clearFrameBuffer: Boolean = true) extends Module {
  val io = IO(new Bundle {
    /** Control port */
    val ctrl = SpriteCtrlIO()
    /** Sprite bank */
    val bank = Input(Bool())
    /** Frame buffer port */
    val frameBuffer = new SpriteFrameBufferIO
    /** Debug port */
    val debug = Output(new Bundle {
      val idle = Bool()
      val clear = Bool()
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
    val idle :: clear :: load :: latch :: check :: ready :: pending :: next :: done :: Nil = Enum(9)
  }

  // Set 8BPP flag
  val is8BPP = io.ctrl.format === Config.GFX_FORMAT_8BPP.U

  // Decode the sprite
  val sprite = Sprite.decode(io.ctrl.vram.dout, io.ctrl.zoom)

  // Wires
  val effectiveRead = Wire(Bool())

  // Registers
  val stateReg = RegInit(State.idle)
  val spriteReg = RegEnable(sprite, stateReg === State.latch)
  val numTilesReg = RegEnable(spriteReg.cols * spriteReg.rows, stateReg === State.check)
  val burstPendingReg = RegInit(false.B)

  // The FIFO is used to buffer tile ROM data to be processed by the sprite decoder
  val fifo = Module(new Queue(Bits(Config.TILE_ROM_DATA_WIDTH.W), SpriteProcessor.FIFO_DEPTH, useSyncReadMem = true, hasFlush = true))
  fifo.flush := stateReg === State.idle

  // Counters
  val (clearAddr, clearDone) = Counter.static(Config.SCREEN_WIDTH * Config.SCREEN_HEIGHT, enable = stateReg === State.clear)
  val (spriteCounter, spriteCounterWrap) = Counter.static(maxSprites, stateReg === State.next)
  val (tileCounter, tileCounterWrap) = Counter.dynamic(numTilesReg, effectiveRead)

  // Sprite blitter
  val blitter = Module(new SpriteBlitter)

  // Sprite decoder
  val decoder = Module(new SpriteDecoder)
  decoder.io.format := io.ctrl.format
  decoder.io.tileRom <> fifo.io.deq
  decoder.io.pixelData <> blitter.io.pixelData

  // Read tile ROM data when the FIFO is half-empty (i.e. there is enough room to fit another burst)
  val tileRomRead = stateReg === State.pending &&
    !burstPendingReg &&
    fifo.io.count <= (SpriteProcessor.FIFO_DEPTH / 2).U

  // Set effective read flag
  effectiveRead := tileRomRead && !io.ctrl.tileRom.waitReq

  // Set the sprite RAM address. The sprite counter is padded to 10-bits (1024) to handle a lower
  // maximum sprite count (during testing).
  val spriteRamAddr = io.bank ## spriteCounter.pad(10)

  // Set tile ROM address
  val tileRomAddr = (spriteReg.code + tileCounter) << Mux(is8BPP, 8.U, 7.U)

  // Set tile ROM burst length
  val tileRomBurstLength = Mux(is8BPP, 32.U, 16.U)

  // The burst pending register is asserted when there is a burst in progress
  when(io.ctrl.tileRom.burstDone) {
    burstPendingReg := false.B
  }.elsewhen(effectiveRead) {
    burstPendingReg := true.B
  }

  // Enqueue the blitter configuration when the blitter is ready
  when(stateReg === State.ready) {
    val config = Wire(new SpriteBlitterConfig)
    config.sprite := spriteReg
    config.flip := io.ctrl.flip
    config.rotate := io.ctrl.rotate
    blitter.io.config.enq(config)
  } otherwise {
    blitter.io.config.noenq()
  }

  // Enqueue tile ROM data in the FIFO when it is available
  when(io.ctrl.tileRom.valid) {
    fifo.io.enq.enq(io.ctrl.tileRom.dout)
  } otherwise {
    fifo.io.enq.noenq()
  }

  // FSM
  switch(stateReg) {
    // Wait for the start signal
    is(State.idle) {
      when(io.ctrl.start) {
        stateReg := (if (clearFrameBuffer) State.clear else State.load)
      }
    }

    // Clears the frame buffer
    is(State.clear) {
      when(clearDone) { stateReg := Mux(io.ctrl.enable, State.load, State.idle) }
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
  io.ctrl.busy := stateReg =/= State.idle
  io.ctrl.vram.rd := stateReg === State.load
  io.ctrl.vram.addr := spriteRamAddr
  io.ctrl.tileRom.rd := tileRomRead
  io.ctrl.tileRom.addr := tileRomAddr
  io.ctrl.tileRom.burstLength := tileRomBurstLength
  io.frameBuffer := Mux(stateReg === State.clear, SpriteProcessor.clearMem(clearAddr), blitter.io.frameBuffer)
  io.debug.idle := stateReg === State.idle
  io.debug.clear := stateReg === State.clear
  io.debug.load := stateReg === State.load
  io.debug.latch := stateReg === State.latch
  io.debug.check := stateReg === State.check
  io.debug.ready := stateReg === State.ready
  io.debug.pending := stateReg === State.ready
  io.debug.next := stateReg === State.next
  io.debug.done := stateReg === State.done

  printf(p"SpriteProcessor(state: $stateReg, spriteCounter: $spriteCounter ($spriteCounterWrap))\n")
}

object SpriteProcessor {
  /** The depth of the tile ROM FIFO in words */
  val FIFO_DEPTH = 64

  /**
   * Returns a virtual write-only memory interface that writes a constant value to the sprite frame
   * buffer.
   *
   * @param addr The memory address.
   * @param data The constant value.
   */
  def clearMem(addr: UInt, data: Bits = 0.U): SpriteFrameBufferIO = {
    val mem = Wire(new SpriteFrameBufferIO)
    mem.wr := true.B
    mem.addr := addr
    mem.mask := 0.U
    mem.din := data
    mem
  }
}
