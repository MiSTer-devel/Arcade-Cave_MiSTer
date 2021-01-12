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

import axon.Util
import axon.mem._
import axon.types._
import axon.util.Counter
import cave._
import cave.types._
import chisel3._
import chisel3.util._

/** GPU control interface. */
class GPUCtrlIO extends Bundle {
  /** Asserted when a frame is requested */
  val gpuStart = Input(Bool())
  /** Asserted when the GPU is ready */
  val gpuReady = Output(Bool())
  /** Asserted when a DMA transfer is requested */
  val dmaStart = Output(Bool())
  /** Asserted when the DMA controller is ready */
  val dmaReady = Input(Bool())
  /** Asserted when the screen is rotated */
  val rotate = Input(Bool())
  /** Asserted when the screen is flipped */
  val flip = Input(Bool())
}

/** Graphics processing unit (GPU). */
class GPU extends Module {
  val io = IO(new Bundle {
    /** Control port */
    val ctrl = new GPUCtrlIO
    /** Video registers port */
    val videoRegs = Input(Bits(Config.VIDEO_REGS_GPU_DATA_WIDTH.W))
    /** Layer 0 registers port */
    val layer0Regs = Input(Bits(Config.LAYER_REGS_GPU_DATA_WIDTH.W))
    /** Layer 1 registers port */
    val layer1Regs = Input(Bits(Config.LAYER_REGS_GPU_DATA_WIDTH.W))
    /** Layer 2 registers port */
    val layer2Regs = Input(Bits(Config.LAYER_REGS_GPU_DATA_WIDTH.W))
    /** Sprite RAM port */
    val spriteRam = ReadMemIO(Config.SPRITE_RAM_GPU_ADDR_WIDTH, Config.SPRITE_RAM_GPU_DATA_WIDTH)
    /** Layer 0 RAM port */
    val layer0Ram = ReadMemIO(Config.LAYER_RAM_GPU_ADDR_WIDTH, Config.LAYER_RAM_GPU_DATA_WIDTH)
    /** Layer 1 RAM port */
    val layer1Ram = ReadMemIO(Config.LAYER_RAM_GPU_ADDR_WIDTH, Config.LAYER_RAM_GPU_DATA_WIDTH)
    /** Layer 2 RAM port */
    val layer2Ram = ReadMemIO(Config.LAYER_RAM_GPU_ADDR_WIDTH, Config.LAYER_RAM_GPU_DATA_WIDTH)
    /** Palette RAM port */
    val paletteRam = ReadMemIO(Config.PALETTE_RAM_GPU_ADDR_WIDTH, Config.PALETTE_RAM_GPU_DATA_WIDTH)
    /** Tile ROM port */
    val tileRom = new TileRomIO
    /** Frame buffer DMA port */
    val frameBufferDMA = Flipped(new FrameBufferDMAIO)
  })

  // States
  object State {
    val idle :: clear :: sprite :: layer0 :: layer1 :: layer2 :: dmaStart :: dmaWait :: Nil = Enum(8)
  }

  // Wires
  val nextState = Wire(UInt())

  // Registers
  val stateReg = RegNext(nextState, State.idle)

  // Counters
  val (x, xWrap) = Counter.static(Config.SCREEN_WIDTH, enable = stateReg === State.clear)
  val (y, clearDone) = Counter.static(Config.SCREEN_HEIGHT, enable = xWrap)

  // Layer processor
  val layerProcessor = Module(new LayerProcessor)
  layerProcessor.io.start := RegNext(
    (stateReg =/= State.layer0 && nextState === State.layer0) ||
    (stateReg =/= State.layer1 && nextState === State.layer1) ||
    (stateReg =/= State.layer2 && nextState === State.layer2)
  )
  layerProcessor.io.layerIndex := MuxLookup(stateReg, 0.U, Seq(State.layer0 -> 0.U, State.layer1 -> 1.U, State.layer2 -> 2.U))
  layerProcessor.io.layerRegs := MuxLookup(stateReg, DontCare, Seq(State.layer0 -> io.layer0Regs, State.layer1 -> io.layer1Regs, State.layer2 -> io.layer2Regs))
  layerProcessor.io.layerRam <> ReadMemIO.demux(stateReg, Seq(State.layer0 -> io.layer0Ram, State.layer1 -> io.layer1Ram, State.layer2 -> io.layer2Ram))

  // Sprite processor
  val spriteProcessor = Module(new SpriteProcessor)
  spriteProcessor.io.start := RegNext(stateReg =/= State.sprite && nextState === State.sprite)
  spriteProcessor.io.spriteBank := io.videoRegs(64)
  spriteProcessor.io.spriteRam <> io.spriteRam

  // The clear memory interface is used for writing blank pixels
  val clearMem = GPU.clearMem(x ## y)

  // The priority RAM is used by the layers for buffering pixel priority data during rendering
  val priorityRam = Module(new DualPortRam(
    addrWidthA = Config.PRIO_BUFFER_ADDR_WIDTH,
    dataWidthA = Config.PRIO_BUFFER_DATA_WIDTH,
    addrWidthB = Config.PRIO_BUFFER_ADDR_WIDTH,
    dataWidthB = Config.PRIO_BUFFER_DATA_WIDTH,
    maskEnable = false
  ))
  priorityRam.io.portA <> WriteMemIO.mux(stateReg, Seq(
    State.clear -> clearMem,
    State.sprite -> spriteProcessor.io.priority.write,
    State.layer0 -> layerProcessor.io.priority.write,
    State.layer1 -> layerProcessor.io.priority.write,
    State.layer2 -> layerProcessor.io.priority.write
  ))
  priorityRam.io.portB <> ReadMemIO.mux(stateReg === State.sprite, spriteProcessor.io.priority.read, layerProcessor.io.priority.read)

  // The frame buffer is used by the sprite and tilemap layers for writing pixel data during
  // rendering.
  //
  // Port A is registered to have better timing, due to the address linearization (which requires a
  // multiplier). Port B is used for DMA when copying the frame buffer to DDR memory.
  val frameBuffer = Module(new DualPortRam(
    addrWidthA = Config.FRAME_BUFFER_ADDR_WIDTH,
    dataWidthA = Config.FRAME_BUFFER_DATA_WIDTH,
    depthA = Some(Config.FRAME_BUFFER_DEPTH),
    addrWidthB = Config.FRAME_BUFFER_DMA_ADDR_WIDTH,
    dataWidthB = Config.FRAME_BUFFER_DATA_WIDTH * Config.FRAME_BUFFER_DMA_PIXELS,
    depthB = Some(Config.FRAME_BUFFER_DMA_DEPTH),
    maskEnable = false
  ))
  frameBuffer.io.portA <> RegNext(WriteMemIO.mux(stateReg, Seq(
    State.clear -> clearMem,
    State.sprite -> spriteProcessor.io.frameBuffer,
    State.layer0 -> layerProcessor.io.frameBuffer,
    State.layer1 -> layerProcessor.io.frameBuffer,
    State.layer2 -> layerProcessor.io.frameBuffer
  )).mapAddr(GPU.linearizeAddr(io.ctrl.rotate, io.ctrl.flip)))
  frameBuffer.io.portB <> io.frameBufferDMA

  // Decode raw pixel data from the frame buffer
  io.frameBufferDMA.dout := GPU
    .decodePixels(frameBuffer.io.portB.dout, Config.FRAME_BUFFER_DMA_PIXELS)
    .reduce(_ ## _)

  // Default to the previous state
  nextState := stateReg

  // FSM
  switch(stateReg) {
    // Wait for a new frame
    is(State.idle) {
      when(io.ctrl.gpuStart) { nextState := State.clear }
    }

    // Clears the frame buffer
    is(State.clear) {
      when(clearDone) { nextState := State.sprite }
    }

    // Renders the sprites
    is(State.sprite) {
      when(spriteProcessor.io.done) { nextState := State.layer0 }
    }

    // Renders layer 0
    is(State.layer0) {
      when(layerProcessor.io.done) { nextState := State.layer1 }
    }

    // Renders layer 1
    is(State.layer1) {
      when(layerProcessor.io.done) { nextState := State.layer2 }
    }

    // Renders layer 2
    is(State.layer2) {
      when(layerProcessor.io.done) { nextState := State.dmaStart }
    }

    // Wait for the frame buffer DMA transfer to start
    is(State.dmaStart) {
      when(io.ctrl.dmaReady) { nextState := State.dmaWait }
    }

    // Wait for the frame buffer DMA transfer to complete
    is(State.dmaWait) {
      when(io.ctrl.dmaReady) { nextState := State.idle }
    }
  }

  // Outputs
  io.ctrl.dmaStart := stateReg === State.dmaStart
  io.ctrl.gpuReady := stateReg === State.idle
  io.paletteRam <> ReadMemIO.mux(stateReg === State.sprite, spriteProcessor.io.paletteRam, layerProcessor.io.paletteRam)
  io.tileRom <> BurstReadMemIO.mux(Seq(
    (stateReg === State.sprite) -> spriteProcessor.io.tileRom,
    (stateReg === State.layer0 || stateReg === State.layer1 || stateReg === State.layer2) -> layerProcessor.io.tileRom
  ))
  io.tileRom.addr := MuxLookup(stateReg, DontCare, Seq(
    State.sprite -> (spriteProcessor.io.tileRom.addr + Config.SPRITE_ROM_OFFSET.U),
    State.layer0 -> (layerProcessor.io.tileRom.addr + Config.LAYER_0_ROM_OFFSET.U),
    State.layer1 -> (layerProcessor.io.tileRom.addr + Config.LAYER_1_ROM_OFFSET.U),
    State.layer2 -> (layerProcessor.io.tileRom.addr + Config.LAYER_2_ROM_OFFSET.U)
  ))
}

object GPU {
  /**
   * Converts an X/Y address to a linear address.
   *
   * @param addr The address value.
   */
  def linearizeAddr(rotate: Bool, flip: Bool)(addr: UInt): UInt = {
    val x = addr.head(log2Up(Config.SCREEN_WIDTH))
    val y = addr.tail(log2Up(Config.SCREEN_WIDTH))
    val x_ = (Config.SCREEN_WIDTH - 1).U - x
    val y_ = (Config.SCREEN_HEIGHT - 1).U - y
    Mux(rotate,
      Mux(flip, (x * Config.SCREEN_HEIGHT.U) + y_, (x_ * Config.SCREEN_HEIGHT.U) + y),
      Mux(flip, (y_ * Config.SCREEN_WIDTH.U) + x_, (y * Config.SCREEN_WIDTH.U) + x)
    )
  }

  /**
   * Creates a virtual write-only memory interface that writes a constant value to the given
   * address.
   *
   * @param addr The address value.
   * @param data The constant value.
   */
  def clearMem(addr: UInt, data: Bits = 0.U): WriteMemIO = {
    val mem = Wire(WriteMemIO(Config.FRAME_BUFFER_ADDR_WIDTH, Config.FRAME_BUFFER_DATA_WIDTH))
    mem.wr := true.B
    mem.addr := addr
    mem.mask := 0.U
    mem.din := data
    mem
  }

  /**
   * Decodes a list of pixels.
   *
   * @param data The pixel data.
   * @param n    The number of pixels.
   * @return A list of 24-bit pixel values.
   */
  def decodePixels(data: Bits, n: Int): Seq[Bits] =
    Util
      // Decode channels
      .decode(data, n * 3, Config.BITS_PER_CHANNEL)
      // Convert channel values to 8BPP
      .map { c => c(4, 0) ## c(4, 2) }
      // Group channels
      .grouped(3).toSeq
      // Reorder channels (BRG -> BGR)
      .map { case Seq(b, r, g) => Cat(b, g, r) }
      // Swap pixels values
      .reverse

  /**
   * Calculates the visibility of a pixel.
   *
   * @param pos The pixel position.
   * @return A boolean value indicating whether the pixel is visible.
   */
  def isVisible(pos: Vec2): Bool =
    Util.between(pos.x, 0 until Config.SCREEN_WIDTH) &&
    Util.between(pos.y, 0 until Config.SCREEN_HEIGHT)

  def isVisible(pos: SVec2): Bool =
    Util.between(pos.x, 0 until Config.SCREEN_WIDTH) &&
    Util.between(pos.y, 0 until Config.SCREEN_HEIGHT)
}
