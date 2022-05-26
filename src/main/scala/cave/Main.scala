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

import axon._
import axon.gfx._
import axon.mem._
import axon.mem.ddr.DDR
import axon.mem.sdram.{SDRAM, SDRAMIO}
import axon.mister._
import axon.snd._
import axon.types._
import cave.fb._
import chisel3._
import chisel3.experimental.FlatIO
import chisel3.stage._

/**
 * The top-level module.
 *
 * The main module abstracts the rest of the arcade hardware from MiSTer-specific things (e.g.
 * memory arbiter, frame buffer) that are not part of the original arcade hardware design.
 */
class Main extends Module {
  val io = FlatIO(new Bundle {
    /** Video clock domain */
    val videoClock = Input(Clock())
    /** Video reset */
    val videoReset = Input(Bool())
    /** CPU reset */
    val cpuReset = Input(Bool())
    /** DDR port */
    val ddr = BurstReadWriteMemIO(Config.ddrConfig)
    /** SDRAM control port */
    val sdram = SDRAMIO(Config.sdramConfig)
    /** Options port */
    val options = OptionsIO()
    /** Joystick port */
    val joystick = JoystickIO()
    /** IOCTL port */
    val ioctl = IOCTL()
    /** Frame buffer control port */
    val frameBufferCtrl = FrameBufferCtrlIO(Config.SCREEN_WIDTH, Config.SCREEN_HEIGHT)
    /** Audio port */
    val audio = Output(new Audio(Config.ymzConfig.sampleWidth))
    /** Video port */
    val video = VideoIO()
    /** RGB output */
    val rgb = Output(RGB(Config.RGB_OUTPUT_BPP.W))
    /** LED port */
    val led = mister.LEDIO()
  })

  // The game configuration register is latched when data is written to the IOCTL (i.e. the game
  // index is set by the MRA file).
  val gameConfigReg = {
    val gameConfig = Reg(GameConfig())
    val latched = RegInit(false.B)
    when(io.ioctl.download && io.ioctl.wr && io.ioctl.index === IOCTL.GAME_INDEX.U) {
      gameConfig := GameConfig(io.ioctl.dout(OptionsIO.GAME_INDEX_WIDTH - 1, 0))
      latched := true.B
    }
    // Default to the game configuration set in the options
    when(Util.falling(io.ioctl.download) && !latched) {
      gameConfig := GameConfig(io.options.gameIndex)
      latched := true.B
    }
    gameConfig
  }

  // Connect IOCTL to DIPs register file
  val dips = Module(new RegisterFile(IOCTL.DATA_WIDTH, Config.DIPS_DEPTH))
  dips.io.mem <> io.ioctl.dips.mapAddr { a => (a >> 1).asUInt }.asReadWriteMemIO // convert from byte address

  // DDR controller
  val ddr = Module(new DDR(Config.ddrConfig))
  ddr.io.ddr <> io.ddr

  // SDRAM controller
  val sdram = Module(new SDRAM(Config.sdramConfig))
  sdram.io.sdram <> io.sdram

  // Memory subsystem
  val memSys = Module(new MemSys)
  memSys.io.gameConfig <> gameConfigReg
  memSys.io.ioctl <> io.ioctl
  memSys.io.ddr <> ddr.io.mem
  memSys.io.sdram <> sdram.io.mem

  // Video subsystem
  val videoSys = Module(new VideoSys)
  videoSys.io.videoClock := io.videoClock
  videoSys.io.videoReset := io.videoReset
  videoSys.io.options <> io.options
  videoSys.io.video <> io.video

  // The Cave module should be reset when the CPU reset signal is asserted (i.e. the user pressed
  // the reset button), or while the memory system is not ready.
  val cave = withReset(io.cpuReset || !memSys.io.ready) { Module(new Cave) }
  cave.io.gameConfig <> gameConfigReg
  cave.io.options <> io.options
  cave.io.dips := dips.io.regs
  cave.io.joystick <> io.joystick
  cave.io.audio <> io.audio
  cave.io.video <> videoSys.io.video
  cave.io.rgb <> io.rgb
  cave.io.rom <> memSys.io.rom

  // Sprite frame buffer
  val spriteFrameBuffer = Module(new SpriteFrameBuffer)
  spriteFrameBuffer.io.enable := io.options.frameBufferEnable.sprite && memSys.io.ready
  spriteFrameBuffer.io.ready := cave.io.spriteFrameBufferReady
  spriteFrameBuffer.io.swap := cave.io.spriteFrameBufferSwap
  spriteFrameBuffer.io.video <> videoSys.io.video
  spriteFrameBuffer.io.gpu.lineBuffer <> cave.io.spriteLineBuffer
  spriteFrameBuffer.io.gpu.frameBuffer <> cave.io.spriteFrameBuffer
  spriteFrameBuffer.io.ddr.lineBuffer <> memSys.io.spriteLineBuffer
  spriteFrameBuffer.io.ddr.frameBuffer <> memSys.io.spriteFrameBuffer

  // System frame buffer
  val systemFrameBuffer = Module(new SystemFrameBuffer)
  systemFrameBuffer.io.enable := io.options.frameBufferEnable.system && memSys.io.ready
  systemFrameBuffer.io.rotate := io.options.rotate
  systemFrameBuffer.io.forceBlank := !memSys.io.ready
  systemFrameBuffer.io.video <> videoSys.io.video
  systemFrameBuffer.io.frameBufferCtrl <> io.frameBufferCtrl
  systemFrameBuffer.io.frameBuffer <> cave.io.systemFrameBuffer
  systemFrameBuffer.io.ddr <> memSys.io.systemFrameBuffer

  // System LED outputs
  io.led.power := false.B
  io.led.disk := io.ioctl.download
  io.led.user := memSys.io.ready
}

object Main extends App {
  (new ChiselStage).execute(
    Array("--compiler", "verilog", "--target-dir", "quartus/rtl"),
    Seq(ChiselGeneratorAnnotation(() => new Main))
  )
}
