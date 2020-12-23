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

import axon.mem._
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
  /** The size of a large tile in pixels */
  val LARGE_TILE_SIZE = 16
  /** The size of a large tile in bytes */
  val LARGE_TILE_BYTE_SIZE = LARGE_TILE_SIZE * 8
  /** The magic position value that indicates a disabled sprite */
  val SPRITE_MAGIC_POS = 0x2a0

  val io = IO(new Bundle {
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
  val spriteInfo = Sprite.decode(io.spriteRam.dout)

  // Wires
  val effectiveRead = Wire(Bool())
  val nextSpriteInfo = Wire(Bool())
  val updateSpriteInfo = Wire(Bool())
  val pipelineDoneBlitting = Wire(Bool())
  val pipelineTakesSpriteInfo = Wire(Bool())
  val fifoFull = Wire(Bool())

  // Registers
  val stateReg = RegInit(State.idle)
  val spriteInfoTaken = Reg(Bool())
  val burstTodo = Reg(Bool())
  val burstReady = Reg(Bool())
  val rawSpriteInfoReg = RegEnable(io.spriteRam.dout, updateSpriteInfo)
  val spriteInfoReg = RegEnable(spriteInfo, updateSpriteInfo)
  val burstCounterMax = RegEnable(spriteInfo.tileSize.x * spriteInfo.tileSize.y, updateSpriteInfo)

  // Counters
  val (spriteInfoCounter, _) = Counter.static(numSprites * 2, // FIXME
    enable = nextSpriteInfo,
    reset = stateReg === State.idle
  )
  val (spriteSentCounter, _) = Counter.static(numSprites,
    enable = updateSpriteInfo,
    reset = stateReg === State.idle
  )
  val (spriteDoneCounter, _) = Counter.static(numSprites,
    enable = pipelineDoneBlitting,
    reset = stateReg === State.idle
  )
  val (burstCounter, burstCounterWrap) = Counter.dynamic(burstCounterMax,
    enable = effectiveRead,
    reset = updateSpriteInfo
  )

  // Tile FIFO
  //
  // TODO: Refactor to queue
  val fifo = Module(new TileFIFO)
  fifo.io.clock := clock
  fifo.io.data := io.tileRom.dout
  fifo.io.wrreq := io.tileRom.valid
  fifoFull := fifo.io.almost_full

  // Sprite blitter
  val spriteBlitter = Module(new SpriteBlitter)
  spriteBlitter.io.clk_i := clock
  spriteBlitter.io.rst_i := reset
  spriteBlitter.io.sprite_info_i := rawSpriteInfoReg
  pipelineTakesSpriteInfo := spriteBlitter.io.get_sprite_info_o
  spriteBlitter.io.pixelData_bits := fifo.io.q
  fifo.io.rdreq := spriteBlitter.io.pixelData_ready
  spriteBlitter.io.pixelData_valid := !fifo.io.empty
  spriteBlitter.io.paletteRam <> io.paletteRam
  spriteBlitter.io.priority <> io.priority
  spriteBlitter.io.frameBuffer <> io.frameBuffer
  pipelineDoneBlitting := spriteBlitter.io.done

  // Set sprite enabled flag
  val spriteInRamLine = spriteInfo.pos.x =/= SPRITE_MAGIC_POS.U &&
                        spriteInfo.tileSize.x =/= 0.U &&
                        spriteInfo.tileSize.y =/= 0.U

  // Set next sprite info flag
  nextSpriteInfo := stateReg === State.working &&
                    !spriteInfoCounter(10) &&
                    (!spriteInRamLine || updateSpriteInfo)

  // Set done flag
  val workDone = spriteInfoCounter(10) &&
                 spriteSentCounter === spriteDoneCounter

  // Set update sprite info flag
  updateSpriteInfo := stateReg === State.working &&
                      !spriteInfoCounter(10) &&
                      spriteInRamLine &&
                      spriteInfoTaken &&
                      !burstTodo

  // Set burst done flag
  val doneBursting = burstTodo && burstCounterWrap

  // Set sprite burst read flag
  val spriteBurstRead = burstTodo && burstReady && !fifoFull

  // Set effective read flag
  effectiveRead := spriteBurstRead && !io.tileRom.waitReq

  // Set sprite RAM address
  val spriteRamAddr = io.spriteBank ## spriteInfoCounter(9, 0)

  // Set tile ROM address
  val tileRomAddr = (spriteInfoReg.code + burstCounter) * LARGE_TILE_BYTE_SIZE.U

  // Toggle sprite info taken register
  when(stateReg === State.idle) {
    spriteInfoTaken := true.B
  }.elsewhen(pipelineTakesSpriteInfo) {
    spriteInfoTaken := true.B
  }.elsewhen(updateSpriteInfo) {
    spriteInfoTaken := false.B
  }

  // Toggle burst todo register
  when(stateReg === State.idle) {
    burstTodo := false.B
  }.elsewhen(updateSpriteInfo) {
    burstTodo := true.B
  }.elsewhen(doneBursting) {
    burstTodo := false.B
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
  io.tileRom.rd := spriteBurstRead
  io.tileRom.addr := tileRomAddr + Config.TILE_ROM_OFFSET.U
  io.tileRom.burstLength := 16.U
}
