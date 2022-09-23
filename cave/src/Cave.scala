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

package cave

import arcadia._
import arcadia.gfx._
import arcadia.mem._
import arcadia.mem.ddr.DDR
import arcadia.mem.sdram.{SDRAM, SDRAMIO}
import arcadia.mister._
import cave.fb._
import cave.gfx.GPU
import cave.main.Main
import cave.snd.Sound
import chisel3._
import chisel3.util._
import chisel3.experimental.FlatIO

/**
 * The top-level module.
 *
 * This module abstracts the rest of the arcade hardware from MiSTer-specific things (e.g. memory
 * subsystem) that are not part of the original arcade hardware design.
 */
class Cave extends Module {
  val io = FlatIO(new Bundle {
    /** CPU clock */
    val cpuClock = Input(Clock())
    /** CPU reset */
    val cpuReset = Input(Bool())
    /** Video clock */
    val videoClock = Input(Clock())
    /** Video reset */
    val videoReset = Input(Bool())
    /** Options port */
    val options = Input(OptionsIO())
    /** Player port */
    val player = Input(Vec(2, PlayerIO()))
    /** IOCTL port */
    val ioctl = IOCTL()
    /** LED port */
    val led = LEDIO()
    /** Frame buffer control port */
    val frameBufferCtrl = FrameBufferCtrlIO()
    /** Video port */
    val video = Output(VideoIO())
    /** RGB output */
    val rgb = Output(UInt(Config.RGB_WIDTH.W))
    /** Audio port */
    val audio = Output(SInt(Config.AUDIO_SAMPLE_WIDTH.W))
    /** SDRAM control port */
    val sdram = SDRAMIO(Config.sdramConfig)
    /** DDR port */
    val ddr = BurstMemIO(Config.ddrConfig)
  })

  io.ioctl.default()

  val vBlank = ShiftRegister(io.video.vBlank, 2)
  val vBlankFalling = Util.falling(vBlank)

  // The game index register is latched when data is written to the IOCTL (i.e. the game index is
  // set by the MRA file), and falls back to the value in the options.
  val gameIndexReg = {
    val reg = Reg(UInt(4.W))
    val latched = RegInit(false.B)
    when(io.ioctl.download && io.ioctl.wr && io.ioctl.index === IOCTL.GAME_INDEX.U) {
      reg := io.ioctl.dout(OptionsIO.GAME_INDEX_WIDTH - 1, 0)
      latched := true.B
    }
    when(Util.falling(io.ioctl.download) && !latched) {
      reg := io.options.gameIndex
      latched := true.B
    }
    reg
  }

  // Game configuration
  val gameConfig = GameConfig(gameIndexReg)

  // Connect IOCTL to DIPs register file
  val dipsRegs = Module(new RegisterFile(IOCTL.DATA_WIDTH, Config.DIPS_REGS_COUNT))
  dipsRegs.io.mem <> io.ioctl.dips
    .mapAddr { a => (a >> 1).asUInt } // convert from byte address
    .asMemIO

  // DDR controller
  val ddr = Module(new DDR(Config.ddrConfig))
  ddr.io.ddr <> io.ddr

  // SDRAM controller
  val sdram = Module(new SDRAM(Config.sdramConfig))
  sdram.io.sdram <> io.sdram

  // Memory subsystem
  val memSys = Module(new MemSys)
  memSys.io.gameConfig := gameConfig
  memSys.io.prog.rom <> io.ioctl.rom
  memSys.io.prog.nvram <> io.ioctl.nvram
  memSys.io.prog.done := Util.falling(io.ioctl.download) && io.ioctl.index === IOCTL.ROM_INDEX.U
  memSys.io.ddr <> ddr.io.mem
  memSys.io.sdram <> sdram.io.mem

  // Video subsystem
  val videoSys = Module(new VideoSys)
  videoSys.io.videoClock := io.videoClock
  videoSys.io.videoReset := io.videoReset
  videoSys.io.prog.video <> io.ioctl.video
  videoSys.io.prog.done := Util.falling(io.ioctl.download) && io.ioctl.index === IOCTL.VIDEO_INDEX.U
  videoSys.io.options := io.options

  // Main PCB
  val main = withClockAndReset(io.cpuClock, io.cpuReset || !memSys.io.ready) { Module(new Main) }
  main.io.videoClock := io.videoClock
  main.io.spriteClock := clock
  main.io.gameIndex := gameIndexReg
  main.io.options := io.options
  main.io.dips := dipsRegs.io.regs
  main.io.player <> io.player
  main.io.video := videoSys.io.video
  main.io.progRom <> Crossing.freeze(io.cpuClock, memSys.io.progRom)
  main.io.eeprom <> Crossing.freeze(io.cpuClock, memSys.io.eeprom)

  // Sound PCB
  val sound = withClockAndReset(io.cpuClock, io.cpuReset || !memSys.io.ready) { Module(new Sound) }
  sound.io.gameIndex := gameIndexReg
  sound.io.gameConfig := gameConfig
  sound.io.ctrl <> main.io.soundCtrl
  sound.io.rom(0) <> Crossing.freeze(io.cpuClock, memSys.io.soundRom(0))
  sound.io.rom(1) <> Crossing.freeze(io.cpuClock, memSys.io.soundRom(1))

  // Graphics processor
  val gpu = Module(new GPU)
  gpu.io.videoClock := io.videoClock
  0.until(Config.LAYER_COUNT).foreach { i =>
    gpu.io.layerCtrl(i).enable := io.options.layer(i)
    gpu.io.layerCtrl(i).format := gameConfig.layer(i).format
    gpu.io.layerCtrl(i).vram8x8 <> main.io.gpuMem.layer(i).vram8x8
    gpu.io.layerCtrl(i).vram16x16 <> main.io.gpuMem.layer(i).vram16x16
    gpu.io.layerCtrl(i).lineRam <> main.io.gpuMem.layer(i).lineRam
    gpu.io.layerCtrl(i).tileRom <> Crossing.syncronize(io.videoClock, memSys.io.layerTileRom(i))
    gpu.io.layerCtrl(i).regs := main.io.gpuMem.layer(i).regs
  }
  gpu.io.spriteCtrl.enable := io.options.sprite
  gpu.io.spriteCtrl.format := gameConfig.sprite.format
  gpu.io.spriteCtrl.start := vBlankFalling
  gpu.io.spriteCtrl.zoom := gameConfig.sprite.zoom
  gpu.io.spriteCtrl.vram <> main.io.gpuMem.sprite.vram
  gpu.io.spriteCtrl.tileRom <> memSys.io.spriteTileRom
  gpu.io.spriteCtrl.regs := main.io.gpuMem.sprite.regs
  gpu.io.gameConfig := gameConfig
  gpu.io.options := io.options
  gpu.io.video := videoSys.io.video
  gpu.io.paletteRam <> main.io.gpuMem.paletteRam

  // Sprite frame buffer
  val spriteFrameBuffer = Module(new SpriteFrameBuffer)
  spriteFrameBuffer.io.videoClock := io.videoClock
  spriteFrameBuffer.io.enable := memSys.io.ready
  spriteFrameBuffer.io.swap := main.io.spriteFrameBufferSwap
  spriteFrameBuffer.io.video := videoSys.io.video
  spriteFrameBuffer.io.lineBuffer <> gpu.io.spriteLineBuffer
  spriteFrameBuffer.io.frameBuffer <> gpu.io.spriteFrameBuffer
  spriteFrameBuffer.io.ddr <> memSys.io.spriteFrameBuffer

  // System frame buffer
  val systemFrameBuffer = Module(new SystemFrameBuffer)
  systemFrameBuffer.io.videoClock := io.videoClock
  systemFrameBuffer.io.enable := memSys.io.ready
  systemFrameBuffer.io.rotate := io.options.rotate
  systemFrameBuffer.io.forceBlank := !memSys.io.ready
  systemFrameBuffer.io.video := videoSys.io.video
  systemFrameBuffer.io.frameBufferCtrl <> io.frameBufferCtrl
  systemFrameBuffer.io.frameBuffer <> gpu.io.systemFrameBuffer
  systemFrameBuffer.io.ddr <> memSys.io.systemFrameBuffer

  // Outputs
  io.video := videoSys.io.video
  io.rgb := gpu.io.rgb
  io.audio := sound.io.audio
  io.led.power := false.B
  io.led.disk := io.ioctl.download
  io.led.user := memSys.io.ready
}
