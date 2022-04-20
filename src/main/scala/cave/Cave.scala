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
import axon.cpu.m68k._
import axon.gfx._
import axon.mem._
import axon.snd._
import axon.types._
import cave.gpu._
import cave.types._
import chisel3._
import chisel3.util._

/**
 * Represents the first-generation CAVE arcade hardware.
 *
 * This module contains the CPU, GPU, sound processor, RAM, ROM, and memory maps.
 */
class Cave extends Module {
  val io = IO(new Bundle {
    /** Video clock domain */
    val videoClock = Input(Clock())
    /** Video reset */
    val videoReset = Input(Bool())
    /** CPU clock domain */
    val cpuClock = Input(Clock())
    /** CPU reset */
    val cpuReset = Input(Reset())
    /** Video port */
    val video = Flipped(VideoIO())
    /** Game config port */
    val gameConfig = Input(GameConfig())
    /** Options port */
    val options = OptionsIO()
    /** Joystick port */
    val joystick = JoystickIO()
    /** Program ROM port */
    val progRom = new ProgRomIO
    /** Sound ROM port */
    val soundRom = new SoundRomIO
    /** EEPROM port */
    val eeprom = new EEPROMIO
    /** Layer 0 tile ROM port */
    val layer0Rom = new LayerRomIO
    /** Layer 1 tile ROM port */
    val layer1Rom = new LayerRomIO
    /** Layer 2 tile ROM port */
    val layer2Rom = new LayerRomIO
    /** Sprite tile ROM port */
    val spriteRom = new SpriteRomIO
    /** Audio port */
    val audio = Output(new Audio(Config.ymzConfig.sampleWidth))
    /** RGB output */
    val rgb = Output(new RGB(Config.DDR_FRAME_BUFFER_BITS_PER_CHANNEL))
  })

  // Wires
  val frameStart = WireInit(false.B)
  val intAck = Wire(Bool())

  // A write-only memory interface is used to connect the CPU to the EEPROM
  val eepromMem = Wire(WriteMemIO(CPU.ADDR_WIDTH, CPU.DATA_WIDTH))

  // The GPU runs in the video clock domain
  val gpu = Module(new GPU)
  gpu.io.videoClock := io.videoClock
  gpu.io.videoReset := io.videoReset
  gpu.io.video <> io.video
  gpu.io.gameConfig <> io.gameConfig
  gpu.io.options <> io.options
  gpu.io.frameReady := Util.rising(ShiftRegister(frameStart, 2))
  gpu.io.layer0Rom <> io.layer0Rom
  gpu.io.layer1Rom <> io.layer1Rom
  gpu.io.layer2Rom <> io.layer2Rom
  gpu.io.spriteRom <> io.spriteRom
  gpu.io.rgb <> io.rgb

  // The CPU and registers run in the CPU clock domain
  withClockAndReset(io.cpuClock, io.cpuReset) {
    // Registers
    val vBlank = ShiftRegister(io.video.vBlank, 2)
    val videoIRQ = RegInit(false.B)
    val iplReg = RegInit(0.U)
    val pauseReg = Util.toggle(Util.rising(io.joystick.player1.pause || io.joystick.player2.pause))

    // M68K CPU
    val cpu = Module(new CPU)
    cpu.io.halt := pauseReg
    cpu.io.dtack := false.B
    cpu.io.vpa := intAck // autovectored interrupts
    cpu.io.ipl := iplReg
    cpu.io.din := 0.U

    // EEPROM
    val eeprom = Module(new EEPROM)
    eeprom.io.mem <> io.eeprom
    val cs = Mux(io.gameConfig.index === GameConfig.GUWANGE.U, eepromMem.din(5), eepromMem.din(9))
    val sck = Mux(io.gameConfig.index === GameConfig.GUWANGE.U, eepromMem.din(6), eepromMem.din(10))
    val sdi = Mux(io.gameConfig.index === GameConfig.GUWANGE.U, eepromMem.din(7), eepromMem.din(11))
    eeprom.io.serial.cs := RegEnable(cs, false.B, eepromMem.wr)
    eeprom.io.serial.sck := RegEnable(sck, false.B, eepromMem.wr)
    eeprom.io.serial.sdi := RegEnable(sdi, false.B, eepromMem.wr)

    // Main RAM
    val mainRam = Module(new SinglePortRam(
      addrWidth = Config.MAIN_RAM_ADDR_WIDTH,
      dataWidth = Config.MAIN_RAM_DATA_WIDTH,
      maskEnable = true
    ))

    // Sprite RAM
    val spriteRam = Module(new TrueDualPortRam(
      addrWidthA = Config.SPRITE_RAM_ADDR_WIDTH,
      dataWidthA = Config.SPRITE_RAM_DATA_WIDTH,
      addrWidthB = Config.SPRITE_RAM_GPU_ADDR_WIDTH,
      dataWidthB = Config.SPRITE_RAM_GPU_DATA_WIDTH,
      maskEnable = true
    ))
    spriteRam.io.clockB := clock // system (i.e. fast) clock domain

    // Layer 0 VRAM
    val layer0Ram = Module(new TrueDualPortRam(
      addrWidthA = Config.LAYER_RAM_ADDR_WIDTH,
      dataWidthA = Config.LAYER_RAM_DATA_WIDTH,
      addrWidthB = Config.LAYER_RAM_GPU_ADDR_WIDTH,
      dataWidthB = Config.LAYER_RAM_GPU_DATA_WIDTH,
      maskEnable = true
    ))
    layer0Ram.io.clockB := io.videoClock

    // Layer 1 VRAM
    val layer1Ram = Module(new TrueDualPortRam(
      addrWidthA = Config.LAYER_RAM_ADDR_WIDTH,
      dataWidthA = Config.LAYER_RAM_DATA_WIDTH,
      addrWidthB = Config.LAYER_RAM_GPU_ADDR_WIDTH,
      dataWidthB = Config.LAYER_RAM_GPU_DATA_WIDTH,
      maskEnable = true
    ))
    layer1Ram.io.clockB := io.videoClock

    // Layer 2 VRAM
    val layer2Ram = Module(new TrueDualPortRam(
      addrWidthA = Config.LAYER_RAM_ADDR_WIDTH,
      dataWidthA = Config.LAYER_RAM_DATA_WIDTH,
      addrWidthB = Config.LAYER_RAM_GPU_ADDR_WIDTH,
      dataWidthB = Config.LAYER_RAM_GPU_DATA_WIDTH,
      maskEnable = true
    ))
    layer2Ram.io.clockB := io.videoClock

    // Palette RAM
    val paletteRam = Module(new TrueDualPortRam(
      addrWidthA = Config.PALETTE_RAM_ADDR_WIDTH,
      dataWidthA = Config.PALETTE_RAM_DATA_WIDTH,
      addrWidthB = Config.PALETTE_RAM_GPU_ADDR_WIDTH,
      dataWidthB = Config.PALETTE_RAM_GPU_DATA_WIDTH,
      maskEnable = true
    ))
    paletteRam.io.clockB := io.videoClock

    // Layer registers
    val layer0Regs = Module(new RegisterFile(Config.LAYER_REGS_COUNT))
    val layer1Regs = Module(new RegisterFile(Config.LAYER_REGS_COUNT))
    val layer2Regs = Module(new RegisterFile(Config.LAYER_REGS_COUNT))

    // Video registers
    val videoRegs = Module(new RegisterFile(Config.VIDEO_REGS_COUNT))

    // GPU
    gpu.io.videoRegs := videoRegs.io.regs.asUInt
    gpu.io.layer0 := Layer.decode(0.U, Mux(io.options.layer.layer0, io.gameConfig.layer0Format, Config.GFX_FORMAT_DISABLE.U), layer0Regs.io.regs.asUInt)
    gpu.io.layer1 := Layer.decode(1.U, Mux(io.options.layer.layer1, io.gameConfig.layer1Format, Config.GFX_FORMAT_DISABLE.U), layer1Regs.io.regs.asUInt)
    gpu.io.layer2 := Layer.decode(2.U, Mux(io.options.layer.layer2, io.gameConfig.layer2Format, Config.GFX_FORMAT_DISABLE.U), layer2Regs.io.regs.asUInt)
    gpu.io.layer0Ram <> layer0Ram.io.portB
    gpu.io.layer1Ram <> layer1Ram.io.portB
    gpu.io.layer2Ram <> layer2Ram.io.portB
    gpu.io.spriteRam <> spriteRam.io.portB
    gpu.io.paletteRam <> paletteRam.io.portB

    // YMZ280B
    val ymz = Module(new YMZ280B(Config.ymzConfig))
    ymz.io.mem <> io.soundRom
    io.audio <> RegEnable(ymz.io.audio.bits, ymz.io.audio.valid)
    val soundIRQ = ymz.io.irq

    // Interrupt acknowledge
    intAck := cpu.io.as && cpu.io.fc === 7.U

    // Set and clear interrupt priority level register
    when(videoIRQ || soundIRQ) { iplReg := 1.U }.elsewhen(intAck) { iplReg := 0.U }

    // Set vertical blank IRQ
    when(Util.rising(vBlank)) { videoIRQ := true.B }

    // Set memory interface defaults, the actual values are assigned in the memory map
    io.progRom.default()
    mainRam.io.default()
    spriteRam.io.portA.default()
    layer0Ram.io.portA.default()
    layer1Ram.io.portA.default()
    layer2Ram.io.portA.default()
    layer0Regs.io.mem.default()
    layer1Regs.io.mem.default()
    layer2Regs.io.mem.default()
    paletteRam.io.portA.default()
    videoRegs.io.mem.default()
    ymz.io.cpu.default()
    eepromMem.default()

    // Set input ports
    val (input0, input1) = Cave.encodePlayers(io.gameConfig.index, io.joystick, eeprom)

    // Memory map
    val map = new MemMap(cpu.io)

    // Access to 0x11xxxx appears during the service menu. It must be ignored, otherwise the service
    // menu freezes.
    map(0x110000 to 0x2fffff).ignore()

    // Dangun Feveron
    when(io.gameConfig.index === GameConfig.DFEVERON.U) {
      map(0x000000 to 0x0fffff).readMemT(io.progRom)(addr => addr ## 0.U)
      map(0x100000 to 0x10ffff).readWriteMem(mainRam.io)
      map(0x300000 to 0x300003).readWriteMem(ymz.io.cpu)
      map(0x400000 to 0x40ffff).readWriteMem(spriteRam.io.portA)
      map(0x500000 to 0x507fff).readWriteMem(layer0Ram.io.portA)
      map(0x600000 to 0x607fff).readWriteMem(layer1Ram.io.portA)
      map(0x708000 to 0x708fff).readWriteMemT(paletteRam.io.portA)(a => a(10, 0))
      map(0x710000 to 0x717fff).readWriteMem(layer2Ram.io.portA)
      map(0x800000 to 0x80007f).writeMem(videoRegs.io.mem.asWriteMemIO)
      map(0x800004).w { (_, _, data) => frameStart := data === 0x01f0.U }
      map(0x800000 to 0x800007).r { (_, offset) =>
        when(offset === 4.U) { videoIRQ := false.B }
        "b001".U ## !videoIRQ
      }
      map(0x900000 to 0x900005).readWriteMem(layer0Regs.io.mem)
      map(0xa00000 to 0xa00005).readWriteMem(layer1Regs.io.mem)
      map(0xb00000).r { (_, _) => input0 }
      map(0xb00002).r { (_, _) => input1 }
      map(0xc00000).writeMem(eepromMem)
    }

    // DoDonPachi
    when(io.gameConfig.index === GameConfig.DDONPACH.U) {
      map(0x000000 to 0x0fffff).readMemT(io.progRom)(addr => addr ## 0.U)
      map(0x100000 to 0x10ffff).readWriteMem(mainRam.io)
      map(0x300000 to 0x300003).readWriteMem(ymz.io.cpu)
      map(0x400000 to 0x40ffff).readWriteMem(spriteRam.io.portA)
      map(0x500000 to 0x507fff).readWriteMem(layer0Ram.io.portA)
      map(0x600000 to 0x607fff).readWriteMem(layer1Ram.io.portA)
      // Access to address 0x5fxxxx occurs during the attract loop on the air stage at frame 9355
      // (i.e. after roughly 150 sec). The game is accessing data relative to a layer 1 address and
      // underflows. These accesses do nothing, but should be acknowledged in order not to block the
      // CPU.
      //
      // The reason these accesses appear is probably because it made the layer update routine
      // simpler to write (no need to handle edge cases). These accesses are simply ignored by the
      // hardware.
      map(0x5f0000 to 0x5fffff).ignore()
      map(0x700000 to 0x70ffff).readWriteMemT(layer2Ram.io.portA)(a => a(12, 0))
      map(0x800000 to 0x80007f).writeMem(videoRegs.io.mem.asWriteMemIO)
      map(0x800004).w { (_, _, data) => frameStart := data === 0x01f0.U }
      map(0x800000 to 0x800007).r { (_, offset) =>
        when(offset === 0.U) { videoIRQ := false.B }
        "b011".U ## !videoIRQ
      }
      map(0x900000 to 0x900005).readWriteMem(layer0Regs.io.mem)
      map(0xa00000 to 0xa00005).readWriteMem(layer1Regs.io.mem)
      map(0xb00000 to 0xb00005).readWriteMem(layer2Regs.io.mem)
      map(0xc00000 to 0xc0ffff).readWriteMem(paletteRam.io.portA)
      map(0xd00000).r { (_, _) => input0 }
      map(0xd00002).r { (_, _) => input1 }
      map(0xe00000).writeMem(eepromMem)
    }

    // ESP Ra.De.
    when(io.gameConfig.index === GameConfig.ESPRADE.U) {
      map(0x000000 to 0x0fffff).readMemT(io.progRom)(addr => addr ## 0.U)
      map(0x100000 to 0x10ffff).readWriteMem(mainRam.io)
      map(0x300000 to 0x300003).readWriteMem(ymz.io.cpu)
      map(0x400000 to 0x40ffff).readWriteMem(spriteRam.io.portA)
      map(0x500000 to 0x507fff).readWriteMem(layer0Ram.io.portA)
      map(0x600000 to 0x607fff).readWriteMem(layer1Ram.io.portA)
      map(0x700000 to 0x707fff).readWriteMem(layer2Ram.io.portA)
      map(0x800000 to 0x80007f).writeMem(videoRegs.io.mem.asWriteMemIO)
      map(0x800004).w { (_, _, data) => frameStart := data === 0x01f0.U }
      map(0x800000 to 0x800007).r { (_, offset) =>
        when(offset === 4.U) { videoIRQ := false.B }
        "b001".U ## !videoIRQ
      }
      map(0x800008 to 0x800fff).ignore()
      map(0x900000 to 0x900005).readWriteMem(layer0Regs.io.mem)
      map(0xa00000 to 0xa00005).readWriteMem(layer1Regs.io.mem)
      map(0xb00000 to 0xb00005).readWriteMem(layer2Regs.io.mem)
      map(0xc00000 to 0xc0ffff).readWriteMem(paletteRam.io.portA)
      map(0xd00000).r { (_, _) => input0 }
      map(0xd00002).r { (_, _) => input1 }
      map(0xe00000).writeMem(eepromMem)
    }

    // Guwange
    when(io.gameConfig.index === GameConfig.GUWANGE.U) {
      map(0x000000 to 0x0fffff).readMemT(io.progRom)(addr => addr ## 0.U)
      map(0x200000 to 0x20ffff).readWriteMem(mainRam.io)
      map(0x300000 to 0x30007f).writeMem(videoRegs.io.mem.asWriteMemIO)
      map(0x300000 to 0x300007).r { (_, offset) =>
        when(offset === 4.U) { videoIRQ := false.B }
        "b001".U ## !videoIRQ
      }
      map(0x300008).w { (_, _, _) => frameStart := true.B }
      map(0x300009 to 0x300fff).ignore()
      map(0x400000 to 0x40ffff).readWriteMem(spriteRam.io.portA)
      map(0x500000 to 0x507fff).readWriteMem(layer0Ram.io.portA)
      map(0x600000 to 0x607fff).readWriteMem(layer1Ram.io.portA)
      map(0x700000 to 0x707fff).readWriteMem(layer2Ram.io.portA)
      map(0x800000 to 0x800003).readWriteMem(ymz.io.cpu)
      map(0x900000 to 0x900005).readWriteMem(layer0Regs.io.mem)
      map(0xa00000 to 0xa00005).readWriteMem(layer1Regs.io.mem)
      map(0xb00000 to 0xb00005).readWriteMem(layer2Regs.io.mem)
      map(0xc00000 to 0xc0ffff).readWriteMem(paletteRam.io.portA)
      map(0xd00010 to 0xd00014).ignore()
      map(0xd00010).writeMem(eepromMem)
      map(0xd00010).r { (_, _) => input0 }
      map(0xd00012).r { (_, _) => input1 }
    }

    // Puzzle Uo Poko
    when(io.gameConfig.index === GameConfig.UOPOKO.U) {
      map(0x000000 to 0x0fffff).readMemT(io.progRom)(addr => addr ## 0.U)
      map(0x100000 to 0x10ffff).readWriteMem(mainRam.io)
      map(0x300000 to 0x300003).readWriteMem(ymz.io.cpu)
      map(0x400000 to 0x40ffff).readWriteMem(spriteRam.io.portA)
      map(0x500000 to 0x507fff).readWriteMem(layer0Ram.io.portA)
      map(0x600000 to 0x60007f).writeMem(videoRegs.io.mem.asWriteMemIO)
      map(0x600000 to 0x600007).r { (_, offset) =>
        when(offset === 4.U) { videoIRQ := false.B }
        "b001".U ## !videoIRQ
      }
      map(0x600008).w { (_, _, _) => frameStart := true.B }
      map(0x600009 to 0x600fff).ignore()
      map(0x700000 to 0x700005).readWriteMem(layer0Regs.io.mem)
      map(0x800000 to 0x80ffff).readWriteMem(paletteRam.io.portA)
      map(0x900000).r { (_, _) => input0 }
      map(0x900002).r { (_, _) => input1 }
      map(0xa00000).writeMem(eepromMem)
    }

    // When the game is paused, request frames at the start of every vertical blank
    when(pauseReg) { frameStart := vBlank }
  }
}

object Cave {
  /**
   * Encodes the joystick IO into bitvector values.
   *
   * @param gameIndex The game index.
   * @param joystick  The joystick interface.
   * @param eeprom    The eeprom interface.
   * @return A pair of bitvectors representing the player inputs.
   */
  private def encodePlayers(gameIndex: UInt, joystick: JoystickIO, eeprom: EEPROM): (UInt, UInt) = {
    val coin1 = Util.pulseSync(Config.PLAYER_COIN_PULSE_WIDTH, joystick.player1.coin)
    val coin2 = Util.pulseSync(Config.PLAYER_COIN_PULSE_WIDTH, joystick.player2.coin)

    val left = Mux(gameIndex === GameConfig.GUWANGE.U,
      Cat(~joystick.player2.buttons, ~joystick.player2.right, ~joystick.player2.left, ~joystick.player2.down, ~joystick.player2.up, ~joystick.player2.start, ~joystick.player1.buttons, ~joystick.player1.right, ~joystick.player1.left, ~joystick.player1.down, ~joystick.player1.up, ~joystick.player1.start),
      Cat("b111111".U, ~joystick.service1, ~coin1, ~joystick.player1.start, ~joystick.player1.buttons, ~joystick.player1.right, ~joystick.player1.left, ~joystick.player1.down, ~joystick.player1.up)
    )

    val right = Mux(gameIndex === GameConfig.GUWANGE.U,
      Cat("b11111111".U, eeprom.io.serial.sdo, "b1111".U, ~joystick.service1, ~coin2, ~coin1),
      Cat("b1111".U, eeprom.io.serial.sdo, "b11".U, ~coin2, ~joystick.player2.start, ~joystick.player2.buttons, ~joystick.player2.right, ~joystick.player2.left, ~joystick.player2.down, ~joystick.player2.up)
    )

    (left, right)
  }
}
