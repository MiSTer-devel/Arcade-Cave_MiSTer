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

package cave.gpu

import axon.Util
import axon.gfx._
import axon.mem._
import axon.types._
import axon.util.Counter
import cave._
import cave.types._
import chisel3._
import chisel3.util._

/** Graphics processing unit (GPU). */
class GPU extends Module {
  val io = IO(new Bundle {
    /** Game config port */
    val gameConfig = Input(GameConfig())
    /** Options port */
    val options = OptionsIO()
    /** Video port */
    val video = Flipped(VideoIO())
    /** Asserted when the program is ready for a new frame */
    val frameReady = Input(Bool())
    /** Asserted when a frame is valid */
    val frameValid = Output(Bool())
    /** Asserted when the DMA controller is ready */
    val dmaReady = Input(Bool())
    /** Video registers port */
    val videoRegs = Input(Bits(Config.VIDEO_REGS_GPU_DATA_WIDTH.W))
    /** Layer 0 port */
    val layer0 = Input(new Layer)
    /** Layer 1 port */
    val layer1 = Input(new Layer)
    /** Layer 2 port */
    val layer2 = Input(new Layer)
    /** Layer 0 VRAM port */
    val layerRam0 = new LayerRamIO
    /** Layer 1 VRAM port */
    val layerRam1 = new LayerRamIO
    /** Layer 2 VRAM port */
    val layerRam2 = new LayerRamIO
    /** Sprite RAM port */
    val spriteRam = new SpriteRamIO
    /** Palette RAM port */
    val paletteRam = new PaletteRamIO
    /** Tile ROM port */
    val tileRom = new TileRomIO
    /** Frame buffer DMA port */
    val frameBufferDMA = Flipped(new FrameBufferDMAIO)
  })

  // States
  object State {
    val idle :: background :: clear :: sprites :: layer0 :: layer1 :: layer2 :: dmaStart :: dmaWait :: Nil = Enum(9)
  }

  // Wires
  val nextState = Wire(UInt())

  // The fill color used to clear the frame buffer must be read from palette RAM. The read request
  // is defined using a memory interface, so that it can be multiplexed with the other palette RAM
  // requests.
  val backgroundColorMem = Wire(ReadMemIO(Config.PALETTE_RAM_GPU_ADDR_WIDTH, Config.PALETTE_RAM_GPU_DATA_WIDTH))
  backgroundColorMem.rd := true.B
  backgroundColorMem.addr := GPU.backgroundPen(io.gameConfig.index).toAddr(io.gameConfig.numColors)

  // Registers
  val stateReg = RegNext(nextState, State.idle)
  val backgroundColorReg = RegEnable(backgroundColorMem.dout, 0.U, stateReg === State.background)

  // Clear counter
  val (clearAddr, clearDone) = Counter.static(Config.SCREEN_WIDTH * Config.SCREEN_HEIGHT, enable = stateReg === State.clear)

  // Sprite processor
  val spriteProcessor = Module(new SpriteProcessor)
  spriteProcessor.io.gameConfig <> io.gameConfig
  spriteProcessor.io.options <> io.options
  spriteProcessor.io.start := stateReg =/= State.sprites && nextState === State.sprites
  spriteProcessor.io.spriteBank := io.videoRegs(64)
  spriteProcessor.io.spriteRam <> io.spriteRam

  // Tilemap processor
  //
  // FIXME: The layer isn't properly set when the start signal is asserted. Ideally, these things
  // should be in place *before* the tilemap processor is started.
  val tilemapProcessor = Module(new TilemapProcessor)
  tilemapProcessor.io.gameConfig <> io.gameConfig
  tilemapProcessor.io.options <> io.options
  tilemapProcessor.io.start := (stateReg =/= State.layer0 && nextState === State.layer0) ||
    (stateReg =/= State.layer1 && nextState === State.layer1) ||
    (stateReg =/= State.layer2 && nextState === State.layer2)
  tilemapProcessor.io.layerIndex := MuxLookup(stateReg, 0.U, Seq(State.layer0 -> 0.U, State.layer1 -> 1.U, State.layer2 -> 2.U))
  tilemapProcessor.io.layer := MuxLookup(stateReg, DontCare, Seq(State.layer0 -> io.layer0, State.layer1 -> io.layer1, State.layer2 -> io.layer2))
  tilemapProcessor.io.layerRam <> ReadMemIO.demux(stateReg, Seq(State.layer0 -> io.layerRam0, State.layer1 -> io.layerRam1, State.layer2 -> io.layerRam2))

  // The priority buffer contains priority data generated by the graphics layers during rendering
  val priorityRam = Module(new DualPortRam(
    addrWidthA = Config.PRIO_BUFFER_ADDR_WIDTH,
    dataWidthA = Config.PRIO_BUFFER_DATA_WIDTH,
    depthA = Some(Config.FRAME_BUFFER_DEPTH),
    addrWidthB = Config.PRIO_BUFFER_ADDR_WIDTH,
    dataWidthB = Config.PRIO_BUFFER_DATA_WIDTH,
    depthB = Some(Config.FRAME_BUFFER_DEPTH)
  ))
  priorityRam.io.portA <> WriteMemIO.mux(stateReg, Seq(
    State.clear -> GPU.clearMem(clearAddr),
    State.sprites -> spriteProcessor.io.priority.write,
    State.layer0 -> tilemapProcessor.io.priority.write,
    State.layer1 -> tilemapProcessor.io.priority.write,
    State.layer2 -> tilemapProcessor.io.priority.write
  ))
  priorityRam.io.portB <> ReadMemIO.mux(stateReg === State.sprites,
    spriteProcessor.io.priority.read,
    tilemapProcessor.io.priority.read
  )

  // The frame buffer contains pixel data generated by the graphics layers during rendering
  val frameBuffer = Module(new DualPortRam(
    addrWidthA = Config.FRAME_BUFFER_ADDR_WIDTH,
    dataWidthA = Config.FRAME_BUFFER_DATA_WIDTH,
    depthA = Some(Config.FRAME_BUFFER_DEPTH),
    addrWidthB = Config.FRAME_BUFFER_DMA_ADDR_WIDTH,
    dataWidthB = Config.FRAME_BUFFER_DATA_WIDTH * Config.FRAME_BUFFER_DMA_PIXELS,
    depthB = Some(Config.FRAME_BUFFER_DMA_DEPTH)
  ))
  frameBuffer.io.portA <> WriteMemIO.mux(stateReg, Seq(
    State.clear -> GPU.clearMem(clearAddr, backgroundColorReg),
    State.sprites -> spriteProcessor.io.frameBuffer,
    State.layer0 -> tilemapProcessor.io.frameBuffer,
    State.layer1 -> tilemapProcessor.io.frameBuffer,
    State.layer2 -> tilemapProcessor.io.frameBuffer
  ))
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
      when(io.frameReady) { nextState := State.background }
    }

    // Latch the background color
    is(State.background) { nextState := State.clear }

    // Clears the frame buffer
    is(State.clear) {
      when(clearDone) { nextState := State.sprites }
    }

    // Renders the sprites
    is(State.sprites) {
      when(!spriteProcessor.io.busy) {
        nextState := Mux(io.gameConfig.numLayers === 0.U, State.dmaStart, State.layer0)
      }
      when(io.options.layer.sprites) {
        priorityRam.io.portA.wr := false.B
        frameBuffer.io.portA.wr := false.B
      }
    }

    // Renders layer 0
    is(State.layer0) {
      when(!tilemapProcessor.io.busy) {
        nextState := Mux(io.gameConfig.numLayers === 1.U, State.dmaStart, State.layer1)
      }
      when(io.options.layer.layer0) {
        priorityRam.io.portA.wr := false.B
        frameBuffer.io.portA.wr := false.B
      }
    }

    // Renders layer 1
    is(State.layer1) {
      when(!tilemapProcessor.io.busy) {
        nextState := Mux(io.gameConfig.numLayers === 2.U, State.dmaStart, State.layer2)
      }
      when(io.options.layer.layer1) {
        priorityRam.io.portA.wr := false.B
        frameBuffer.io.portA.wr := false.B
      }
    }

    // Renders layer 2
    is(State.layer2) {
      when(!tilemapProcessor.io.busy) { nextState := State.dmaStart }
      when(io.options.layer.layer2) {
        priorityRam.io.portA.wr := false.B
        frameBuffer.io.portA.wr := false.B
      }
    }

    // Wait for the frame buffer DMA transfer to start
    is(State.dmaStart) {
      when(io.dmaReady) { nextState := State.dmaWait }
    }

    // Wait for the frame buffer DMA transfer to complete
    is(State.dmaWait) {
      when(io.dmaReady) { nextState := State.idle }
    }
  }

  // Outputs
  io.frameValid := stateReg === State.dmaStart
  io.paletteRam <> ReadMemIO.muxLookup(stateReg, backgroundColorMem, Seq(
    State.sprites -> spriteProcessor.io.paletteRam,
    State.layer0 -> tilemapProcessor.io.paletteRam,
    State.layer1 -> tilemapProcessor.io.paletteRam,
    State.layer2 -> tilemapProcessor.io.paletteRam
  ))
  io.tileRom <> BurstReadMemIO.mux(Seq(
    (stateReg === State.sprites) -> spriteProcessor.io.tileRom,
    (stateReg === State.layer0 || stateReg === State.layer1 || stateReg === State.layer2) -> tilemapProcessor.io.tileRom
  ))
  io.tileRom.addr := MuxLookup(stateReg, DontCare, Seq(
    State.sprites -> (spriteProcessor.io.tileRom.addr + io.gameConfig.spriteRomOffset),
    State.layer0 -> (tilemapProcessor.io.tileRom.addr + io.gameConfig.layer0RomOffset),
    State.layer1 -> (tilemapProcessor.io.tileRom.addr + io.gameConfig.layer1RomOffset),
    State.layer2 -> (tilemapProcessor.io.tileRom.addr + io.gameConfig.layer2RomOffset)
  ))
}

object GPU {
  /**
   * Returns the palette entry used to clear the frame buffer for the given game index.
   *
   * @param index The game index.
   * @return A palette entry.
   */
  def backgroundPen(index: UInt): PaletteEntry =
    MuxLookup(index, PaletteEntry(0x7f.U, 0.U), Seq(
      GameConfig.DFEVERON.U -> PaletteEntry(0x3f.U, 0.U)
    ))

  /**
   * Transforms a frame buffer pixel position to a memory address, applying the optional flip and
   * rotate transforms.
   *
   * @param pos    The pixel position.
   * @param flip   Flips the image.
   * @param rotate Rotates the image 90 degrees.
   * @return An address value.
   */
  def transformAddr(pos: UVec2, flip: Bool, rotate: Bool): UInt = {
    val x = pos.x(Config.FRAME_BUFFER_ADDR_WIDTH_X - 1, 0)
    val y = pos.y(Config.FRAME_BUFFER_ADDR_WIDTH_Y - 1, 0)
    val x_ = (Config.SCREEN_WIDTH - 1).U - x
    val y_ = (Config.SCREEN_HEIGHT - 1).U - y
    Mux(rotate,
      Mux(flip, (x * Config.SCREEN_HEIGHT.U) + y_, (x_ * Config.SCREEN_HEIGHT.U) + y),
      Mux(flip, (y_ * Config.SCREEN_WIDTH.U) + x_, (y * Config.SCREEN_WIDTH.U) + x)
    )
  }

  def transformAddr(pos: SVec2, flip: Bool, rotate: Bool): UInt = {
    val x = pos.x(Config.FRAME_BUFFER_ADDR_WIDTH_X - 1, 0)
    val y = pos.y(Config.FRAME_BUFFER_ADDR_WIDTH_Y - 1, 0)
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
  def isVisible(pos: UVec2): Bool =
    Util.between(pos.x, 0 until Config.SCREEN_WIDTH) &&
      Util.between(pos.y, 0 until Config.SCREEN_HEIGHT)

  def isVisible(pos: SVec2): Bool =
    Util.between(pos.x, 0 until Config.SCREEN_WIDTH) &&
      Util.between(pos.y, 0 until Config.SCREEN_HEIGHT)
}
