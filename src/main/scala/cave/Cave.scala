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
import cave.gfx._
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
    /** Game config port */
    val gameConfig = Input(GameConfig())
    /** Options port */
    val options = OptionsIO()
    /** Joystick port */
    val joystick = JoystickIO()
    /** Video port */
    val video = Flipped(VideoIO())
    /** Audio port */
    val audio = Output(new Audio(Config.ymzConfig.sampleWidth))
    /** RGB output */
    val rgb = Output(RGB(Config.RGB_OUTPUT_BPP.W))
    /** ROM port */
    val rom = new RomIO
    /** Sprite line buffer port */
    val spriteLineBuffer = new SpriteLineBufferIO
    /** Sprite frame buffer port */
    val spriteFrameBuffer = new SpriteFrameBufferIO
    /** System frame buffer port */
    val systemFrameBuffer = new SystemFrameBufferIO
    /** Asserted when the sprite frame buffer should start copying a frame */
    val spriteFrameBufferReady = Output(Bool())
    /** Asserted when the current page for the sprite frame buffer should be swapped */
    val spriteFrameBufferSwap = Output(Bool())
  })

  // Wires
  val intAck = Wire(Bool())

  // A write-only memory interface is used to connect the CPU to the EEPROM
  val eepromMem = Wire(WriteMemIO(CPU.ADDR_WIDTH, CPU.DATA_WIDTH))

  // Registers
  val vBlank = ShiftRegister(io.video.vBlank, 2)
  val videoIrq = RegInit(false.B)
  val agalletIrq = RegInit(false.B)
  val unknownIrq = RegInit(false.B)
  val iplReg = RegInit(0.U)
  val pauseReg = Util.toggle(Util.rising(io.joystick.player1.pause || io.joystick.player2.pause))

  // M68K CPU
  val cpu = Module(new CPU(Config.CPU_CLOCK_DIV))
  val map = new MemMap(cpu.io)
  cpu.io.halt := pauseReg
  cpu.io.dtack := false.B
  cpu.io.vpa := intAck // autovectored interrupts
  cpu.io.ipl := iplReg
  cpu.io.din := 0.U

  // Set program ROM interface defaults
  io.rom.progRom.default()

  // EEPROM
  val eeprom = Module(new EEPROM)
  eeprom.io.mem <> io.rom.eeprom
  val cs = Mux(io.gameConfig.index === GameConfig.GUWANGE.U, eepromMem.din(5), eepromMem.din(9))
  val sck = Mux(io.gameConfig.index === GameConfig.GUWANGE.U, eepromMem.din(6), eepromMem.din(10))
  val sdi = Mux(io.gameConfig.index === GameConfig.GUWANGE.U, eepromMem.din(7), eepromMem.din(11))
  eeprom.io.serial.cs := RegEnable(cs, false.B, eepromMem.wr)
  eeprom.io.serial.sck := RegEnable(sck, false.B, eepromMem.wr)
  eeprom.io.serial.sdi := RegEnable(sdi, false.B, eepromMem.wr)
  eepromMem.default()

  // Main RAM
  val mainRam = Module(new SinglePortRam(
    addrWidth = Config.MAIN_RAM_ADDR_WIDTH,
    dataWidth = Config.MAIN_RAM_DATA_WIDTH,
    maskEnable = true
  ))
  mainRam.io.default()

  // Sprite VRAM
  val spriteRam = Module(new TrueDualPortRam(
    addrWidthA = Config.SPRITE_RAM_ADDR_WIDTH,
    dataWidthA = Config.SPRITE_RAM_DATA_WIDTH,
    addrWidthB = Config.SPRITE_RAM_GPU_ADDR_WIDTH,
    dataWidthB = Config.SPRITE_RAM_GPU_DATA_WIDTH,
    maskEnable = true
  ))
  spriteRam.io.clockB := clock // system (i.e. fast) clock domain
  spriteRam.io.portA.default()

  // Layer VRAM (8x8)
  val vram8x8 = 0.until(Config.LAYER_COUNT).map { _ =>
    val ram = Module(new TrueDualPortRam(
      addrWidthA = Config.LAYER_8x8_RAM_ADDR_WIDTH,
      dataWidthA = Config.LAYER_RAM_DATA_WIDTH,
      addrWidthB = Config.LAYER_8x8_RAM_GPU_ADDR_WIDTH,
      dataWidthB = Config.LAYER_RAM_GPU_DATA_WIDTH,
      maskEnable = true
    ))
    ram.io.clockB := io.video.clock
    ram.io.portA.default()
    ram
  }

  // Layer VRAM (16x16)
  val vram16x16 = 0.until(Config.LAYER_COUNT).map { _ =>
    val ram = Module(new TrueDualPortRam(
      addrWidthA = Config.LAYER_16x16_RAM_ADDR_WIDTH,
      dataWidthA = Config.LAYER_RAM_DATA_WIDTH,
      addrWidthB = Config.LAYER_16x16_RAM_GPU_ADDR_WIDTH,
      dataWidthB = Config.LAYER_RAM_GPU_DATA_WIDTH,
      maskEnable = true
    ))
    ram.io.clockB := io.video.clock
    ram.io.portA.default()
    ram
  }

  // Line RAM
  val lineRam = 0.until(Config.LAYER_COUNT).map { _ =>
    val ram = Module(new TrueDualPortRam(
      addrWidthA = Config.LINE_RAM_ADDR_WIDTH,
      dataWidthA = Config.LINE_RAM_DATA_WIDTH,
      addrWidthB = Config.LINE_RAM_GPU_ADDR_WIDTH,
      dataWidthB = Config.LINE_RAM_GPU_DATA_WIDTH,
      maskEnable = true
    ))
    ram.io.clockB := io.video.clock
    ram.io.portA.default()
    ram
  }

  // Palette RAM
  val paletteRam = Module(new TrueDualPortRam(
    addrWidthA = Config.PALETTE_RAM_ADDR_WIDTH,
    dataWidthA = Config.PALETTE_RAM_DATA_WIDTH,
    addrWidthB = Config.PALETTE_RAM_GPU_ADDR_WIDTH,
    dataWidthB = Config.PALETTE_RAM_GPU_DATA_WIDTH,
    maskEnable = true
  ))
  paletteRam.io.clockB := io.video.clock
  paletteRam.io.portA.default()

  // Layer registers
  val layerRegs = 0.until(Config.LAYER_COUNT).map { _ =>
    val regs = Module(new RegisterFile(Config.LAYER_REGS_COUNT))
    regs.io.mem.default()
    regs
  }

  // Sprite registers
  val spriteRegs = Module(new RegisterFile(Config.VIDEO_REGS_COUNT))
  spriteRegs.io.mem.default()

  // Graphics processor
  val gpu = Module(new GPU)
  gpu.io.gameConfig <> io.gameConfig
  gpu.io.options <> io.options
  gpu.io.video <> io.video
  gpu.io.paletteRam <> paletteRam.io.portB
  0.until(Config.LAYER_COUNT).foreach { i =>
    gpu.io.layerCtrl(i).format := io.gameConfig.layer(i).format
    gpu.io.layerCtrl(i).enable := io.options.layerEnable.layer(i)
    gpu.io.layerCtrl(i).rowScrollEnable := io.options.rowScrollEnable
    gpu.io.layerCtrl(i).rowSelectEnable := io.options.rowSelectEnable
    gpu.io.layerCtrl(i).regs := withClock(io.video.clock) { ShiftRegister(Layer.decode(layerRegs(i).io.dout), 2) }
    gpu.io.layerCtrl(i).vram8x8 <> vram8x8(i).io.portB
    gpu.io.layerCtrl(i).vram16x16 <> vram16x16(i).io.portB
    gpu.io.layerCtrl(i).lineRam <> lineRam(i).io.portB
    gpu.io.layerCtrl(i).tileRom <> Crossing.syncronize(io.video.clock, io.rom.layerTileRom(i))
  }
  gpu.io.spriteCtrl.format := io.gameConfig.sprite.format
  gpu.io.spriteCtrl.enable := io.options.layerEnable.sprite
  gpu.io.spriteCtrl.start := Util.falling(vBlank)
  gpu.io.spriteCtrl.flip := io.options.flip
  gpu.io.spriteCtrl.rotate := io.options.rotate
  gpu.io.spriteCtrl.zoom := io.gameConfig.sprite.zoom
  gpu.io.spriteCtrl.regs := SpriteRegs.decode(spriteRegs.io.dout)
  gpu.io.spriteCtrl.vram <> spriteRam.io.portB
  gpu.io.spriteCtrl.tileRom <> io.rom.spriteTileRom
  gpu.io.spriteLineBuffer <> io.spriteLineBuffer
  gpu.io.spriteFrameBuffer <> io.spriteFrameBuffer
  gpu.io.systemFrameBuffer <> io.systemFrameBuffer
  gpu.io.rgb <> io.rgb

  // YMZ280B
  val ymz = Module(new YMZ280B(Config.ymzConfig))
  ymz.io.cpu.default()
  ymz.io.mem <> io.rom.soundRom
  io.audio <> RegEnable(ymz.io.audio.bits, ymz.io.audio.valid)
  val soundIrq = ymz.io.irq

  // Interrupt acknowledge
  intAck := cpu.io.as && cpu.io.fc === 7.U

  // Set and clear interrupt priority level register
  when(videoIrq || soundIrq || unknownIrq) { iplReg := 1.U }.elsewhen(intAck) { iplReg := 0.U }

  // Toggle video IRQ
  when(Util.rising(vBlank)) {
    videoIrq := true.B
    agalletIrq := true.B
  }.elsewhen(Util.falling(vBlank)) {
    agalletIrq := false.B
  }

  // IRQ cause handler
  val irqCause = { (_: UInt, offset: UInt) =>
    val a = offset === 0.U && agalletIrq
    val b = unknownIrq
    val c = videoIrq
    when(offset === 4.U) { videoIrq := false.B }
    when(offset === 6.U) { unknownIrq := false.B }
    Cat(!a, !b, !c)
  }

  // Set input ports
  val (input0, input1) = Cave.encodePlayers(io.gameConfig.index, io.joystick, eeprom)

  // Set sprite frame buffer signals
  io.spriteFrameBufferReady := Util.falling(gpu.io.spriteCtrl.busy)
  io.spriteFrameBufferSwap := false.B

  /**
   * Maps video RAM to the given base address.
   *
   * @param baseAddr  The base memory address.
   * @param vram8x8   The 8x8 VRAM memory interface.
   * @param vram16x16 The 16x16 VRAM memory interface.
   * @param lineRam   The line RAM memory interface.
   */
  def vramMap(baseAddr: Int, vram8x8: ReadWriteMemIO, vram16x16: ReadWriteMemIO, lineRam: ReadWriteMemIO): Unit = {
    map((baseAddr + 0x0000) to (baseAddr + 0x0fff)).readWriteMem(vram16x16)
    map((baseAddr + 0x1000) to (baseAddr + 0x17ff)).readWriteMem(lineRam)
    map((baseAddr + 0x1800) to (baseAddr + 0x3fff)).noprw()
    map((baseAddr + 0x4000) to (baseAddr + 0x7fff)).readWriteMem(vram8x8)
  }

  /**
   * Maps sprite registers to the given base address.
   *
   * @param baseAddr The base memory address.
   */
  def vregMap(baseAddr: Int): Unit = {
    map((baseAddr + 0x00) to (baseAddr + 0x0f)).writeMem(spriteRegs.io.mem.asWriteMemIO)
    map(baseAddr + 0x08).w { (_, _, _) => io.spriteFrameBufferSwap := true.B }
    map((baseAddr + 0x0a) to (baseAddr + 0x7f)).noprw()
  }

  // Access to 0x11xxxx appears during the service menu. It must be ignored, otherwise the service
  // menu freezes.
  map(0x110000 to 0x2fffff).noprw()

  // Dangun Feveron
  when(io.gameConfig.index === GameConfig.DFEVERON.U) {
    map(0x000000 to 0x0fffff).readMemT(io.rom.progRom) { _ ## 0.U } // convert to byte address
    map(0x100000 to 0x10ffff).readWriteMem(mainRam.io)
    map(0x300000 to 0x300003).readWriteMem(ymz.io.cpu)
    map(0x400000 to 0x40ffff).readWriteMem(spriteRam.io.portA)
    vramMap(0x500000, vram8x8(0).io.portA, vram16x16(0).io.portA, lineRam(0).io.portA)
    vramMap(0x600000, vram8x8(1).io.portA, vram16x16(1).io.portA, lineRam(1).io.portA)
    map(0x708000 to 0x708fff).readWriteMemT(paletteRam.io.portA)(a => a(10, 0))
    map(0x710c12 to 0x710c1f).noprw() // unused
    vregMap(0x800000)
    map(0x800000 to 0x800007).r(irqCause)
    map(0x900000 to 0x900005).readWriteMem(layerRegs(0).io.mem)
    map(0xa00000 to 0xa00005).readWriteMem(layerRegs(1).io.mem)
    map(0xb00000).r { (_, _) => input0 }
    map(0xb00002).r { (_, _) => input1 }
    map(0xc00000).writeMem(eepromMem)
  }

  // DoDonPachi
  when(io.gameConfig.index === GameConfig.DDONPACH.U) {
    map(0x000000 to 0x0fffff).readMemT(io.rom.progRom) { _ ## 0.U } // convert to byte address
    map(0x100000 to 0x10ffff).readWriteMem(mainRam.io)
    map(0x300000 to 0x300003).readWriteMem(ymz.io.cpu)
    map(0x400000 to 0x40ffff).readWriteMem(spriteRam.io.portA)
    vramMap(0x500000, vram8x8(0).io.portA, vram16x16(0).io.portA, lineRam(0).io.portA)
    // Access to address 0x5fxxxx occurs during the attract loop on the air stage at frame 9355
    // (i.e. after roughly 150 sec). The game is accessing data relative to a layer 1 address and
    // underflows. These accesses do nothing, but should be acknowledged in order not to block the
    // CPU.
    //
    // The reason these accesses appear is probably because it made the layer update routine
    // simpler to write (no need to handle edge cases). These accesses are simply ignored by the
    // hardware.
    map(0x5f0000 to 0x5fffff).noprw()
    vramMap(0x600000, vram8x8(1).io.portA, vram16x16(1).io.portA, lineRam(1).io.portA)
    // DoDonPachi only uses 8x8 VRAM for layer 2, with no line RAM
    map(0x700000 to 0x70ffff).readWriteMemT(vram8x8(2).io.portA)(a => a(12, 0))
    vregMap(0x800000)
    map(0x800000 to 0x800007).r(irqCause)
    map(0x900000 to 0x900005).readWriteMem(layerRegs(0).io.mem)
    map(0xa00000 to 0xa00005).readWriteMem(layerRegs(1).io.mem)
    map(0xb00000 to 0xb00005).readWriteMem(layerRegs(2).io.mem)
    map(0xc00000 to 0xc0ffff).readWriteMem(paletteRam.io.portA)
    map(0xd00000).r { (_, _) => input0 }
    map(0xd00002).r { (_, _) => input1 }
    map(0xe00000).writeMem(eepromMem)
  }

  // ESP Ra.De.
  when(io.gameConfig.index === GameConfig.ESPRADE.U) {
    map(0x000000 to 0x0fffff).readMemT(io.rom.progRom) { _ ## 0.U } // convert to byte address
    map(0x100000 to 0x10ffff).readWriteMem(mainRam.io)
    map(0x300000 to 0x300003).readWriteMem(ymz.io.cpu)
    map(0x400000 to 0x40ffff).readWriteMem(spriteRam.io.portA)
    vramMap(0x500000, vram8x8(0).io.portA, vram16x16(0).io.portA, lineRam(0).io.portA)
    vramMap(0x600000, vram8x8(1).io.portA, vram16x16(1).io.portA, lineRam(1).io.portA)
    vramMap(0x700000, vram8x8(2).io.portA, vram16x16(2).io.portA, lineRam(2).io.portA)
    vregMap(0x800000)
    map(0x800000 to 0x800007).r(irqCause)
    map(0x900000 to 0x900005).readWriteMem(layerRegs(0).io.mem)
    map(0xa00000 to 0xa00005).readWriteMem(layerRegs(1).io.mem)
    map(0xb00000 to 0xb00005).readWriteMem(layerRegs(2).io.mem)
    map(0xc00000 to 0xc0ffff).readWriteMem(paletteRam.io.portA)
    map(0xd00000).r { (_, _) => input0 }
    map(0xd00002).r { (_, _) => input1 }
    map(0xe00000).writeMem(eepromMem)
  }

  // Guwange
  when(io.gameConfig.index === GameConfig.GUWANGE.U) {
    map(0x000000 to 0x0fffff).readMemT(io.rom.progRom) { _ ## 0.U } // convert to byte address
    map(0x200000 to 0x20ffff).readWriteMem(mainRam.io)
    map(0x210000 to 0x2fffff).nopr() // access occurs for Guwange (Special)
    vregMap(0x300000)
    map(0x300000 to 0x300007).r(irqCause)
    map(0x300080 to 0x3fffff).nopr() // access occurs for Guwange (Special)
    map(0x400000 to 0x40ffff).readWriteMem(spriteRam.io.portA)
    map(0x410000 to 0x4fffff).nopr() // access occurs for Guwange (Special)
    vramMap(0x500000, vram8x8(0).io.portA, vram16x16(0).io.portA, lineRam(0).io.portA)
    map(0x508000 to 0x5fffff).nopr() // access occurs for Guwange (Special)
    vramMap(0x600000, vram8x8(1).io.portA, vram16x16(1).io.portA, lineRam(1).io.portA)
    map(0x608000 to 0x6fffff).nopr() // access occurs for Guwange (Special)
    vramMap(0x700000, vram8x8(2).io.portA, vram16x16(2).io.portA, lineRam(2).io.portA)
    map(0x800000 to 0x800003).readWriteMem(ymz.io.cpu)
    map(0x900000 to 0x900005).readWriteMem(layerRegs(0).io.mem)
    map(0xa00000 to 0xa00005).readWriteMem(layerRegs(1).io.mem)
    map(0xb00000 to 0xb00005).readWriteMem(layerRegs(2).io.mem)
    map(0xc00000 to 0xc0ffff).readWriteMem(paletteRam.io.portA)
    map(0xd00010 to 0xd00014).noprw()
    map(0xd00010).writeMem(eepromMem)
    map(0xd00010).r { (_, _) => input0 }
    map(0xd00012).r { (_, _) => input1 }
  }

  // Puzzle Uo Poko
  when(io.gameConfig.index === GameConfig.UOPOKO.U) {
    map(0x000000 to 0x0fffff).readMemT(io.rom.progRom) { _ ## 0.U } // convert to byte address
    map(0x100000 to 0x10ffff).readWriteMem(mainRam.io)
    map(0x300000 to 0x300003).readWriteMem(ymz.io.cpu)
    map(0x400000 to 0x40ffff).readWriteMem(spriteRam.io.portA)
    vramMap(0x500000, vram8x8(0).io.portA, vram16x16(0).io.portA, lineRam(0).io.portA)
    vregMap(0x600000)
    map(0x600000 to 0x600007).r(irqCause)
    map(0x700000 to 0x700005).readWriteMem(layerRegs(0).io.mem)
    map(0x800000 to 0x80ffff).readWriteMem(paletteRam.io.portA)
    map(0x900000).r { (_, _) => input0 }
    map(0x900002).r { (_, _) => input1 }
    map(0xa00000).writeMem(eepromMem)
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
      Cat(~joystick.player2.buttons(2, 0), ~joystick.player2.right, ~joystick.player2.left, ~joystick.player2.down, ~joystick.player2.up, ~joystick.player2.start, ~joystick.player1.buttons(2, 0), ~joystick.player1.right, ~joystick.player1.left, ~joystick.player1.down, ~joystick.player1.up, ~joystick.player1.start),
      Cat("b111111".U, ~joystick.service1, ~coin1, ~joystick.player1.start, ~joystick.player1.buttons(2, 0), ~joystick.player1.right, ~joystick.player1.left, ~joystick.player1.down, ~joystick.player1.up)
    )

    val right = Mux(gameIndex === GameConfig.GUWANGE.U,
      Cat("b11111111".U, eeprom.io.serial.sdo, "b1111".U, ~joystick.service1, ~coin2, ~coin1),
      Cat("b1111".U, eeprom.io.serial.sdo, "b11".U, ~coin2, ~joystick.player2.start, ~joystick.player2.buttons(2, 0), ~joystick.player2.right, ~joystick.player2.left, ~joystick.player2.down, ~joystick.player2.up)
    )

    (left, right)
  }
}
