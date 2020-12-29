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

package cave.gpu

import axon._
import axon.mem._
import axon.types._
import axon.util.Counter
import cave._
import cave.types._
import chisel3._
import chisel3.util._

/** Graphics Processor */
class GPU extends Module {
  val io = IO(new Bundle {
    /** Generate a new frame */
    val generateFrame = Input(Bool())
    /** Asserted when the frame is complete */
    val frameDone = Output(Bool())
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
    val idle :: clear :: sprite :: layer0 :: layer1 :: layer2 :: done :: Nil = Enum(7)
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

  // Priority RAM
  //
  // The priority RAM is used by the sprite and tilemap layers for reading/writing pixel priority
  // data during rendering.
  val priorityRam = Module(new DualPortRam(
    addrWidthA = Config.FRAME_BUFFER_ADDR_WIDTH,
    dataWidthA = Config.PRIO_WIDTH,
    addrWidthB = Config.FRAME_BUFFER_ADDR_WIDTH,
    dataWidthB = Config.PRIO_WIDTH,
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

  // Frame buffer
  //
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
    dataWidthB = Config.FRAME_BUFFER_DMA_DATA_WIDTH,
    depthB = Some(Config.FRAME_BUFFER_DMA_DEPTH),
    maskEnable = false
  ))
  frameBuffer.io.portA <> RegNext(WriteMemIO.mux(stateReg, Seq(
    State.clear -> clearMem,
    State.sprite -> spriteProcessor.io.frameBuffer,
    State.layer0 -> layerProcessor.io.frameBuffer,
    State.layer1 -> layerProcessor.io.frameBuffer,
    State.layer2 -> layerProcessor.io.frameBuffer
  )).mapAddr(GPU.linearizeAddr))
  frameBuffer.io.portB <> io.frameBufferDMA

  // Default to the previous state
  nextState := stateReg

  // FSM
  switch(stateReg) {
    // Wait for a new frame
    is(State.idle) {
      when(io.generateFrame) { nextState := State.clear }
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
      when(layerProcessor.io.done) { nextState := State.done }
    }

    // All done
    is(State.done) { nextState := State.idle }
  }

  // Outputs
  io.frameDone := stateReg === State.done
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
  def linearizeAddr(addr: UInt): UInt = {
    val x = addr.head(log2Up(Config.SCREEN_WIDTH))
    val y = addr.tail(log2Up(Config.SCREEN_WIDTH))
    (y * Config.SCREEN_WIDTH.U) + x
  }

  /**
   * Creates a virtual write-only memory interface that writes blank pixels at the given address.
   *
   * @param addr The address value.
   */
  def clearMem(addr: UInt): WriteMemIO = {
    val mem = Wire(WriteMemIO(Config.FRAME_BUFFER_ADDR_WIDTH, Config.FRAME_BUFFER_DATA_WIDTH))
    mem.wr := true.B
    mem.addr := addr
    mem.mask := 0.U
    mem.din := 0.U
    mem
  }

  /**
   * Decodes the palette data into an RGB value.
   *
   * Colors are 15-bit GBR values (i.e. GGGGGBBBBBRRRRR).
   *
   * @param data The palette data.
   */
  def decodePaletteData(data: Bits): RGB = {
    val words = Util.decode(data, 3, Config.BITS_PER_CHANNEL)
    RGB(words(1).asUInt, words(2).asUInt, words(0).asUInt)
  }
}
