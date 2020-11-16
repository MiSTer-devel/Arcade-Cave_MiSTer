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

package cave

import axon.Util
import axon.cpu.m68k._
import axon.gpu._
import axon.mem._
import cave.gpu._
import cave.types._
import chisel3._
import chisel3.util._

/** Represents the CAVE arcade hardware. */
class Cave extends Module {
  val io = IO(new Bundle {
    /** CPU clock domain */
    val cpuClock = Input(Clock())
    /** CPU reset */
    val cpuReset = Input(Reset())
    /** Player port */
    val player = new PlayerIO
    /** Program ROM port */
    val progRom = new ProgRomIO
    /** Tile ROM port */
    val tileRom = new TileRomIO
    /** Frame buffer port */
    val frameBuffer = new FrameBufferIO
    /** Video port */
    val video = Input(new VideoIO)
    /** Debug port */
    val debug = new Bundle {
      val pc = Output(UInt())
      val pcw = Output(Bool())
    }
  })

  // Wires
  val generateFrame = WireInit(false.B)
  val clearIRQ = WireInit(false.B)

  // M68000 CPU
  val cpu = withClockAndReset(io.cpuClock, io.cpuReset) { Module(new CPU) }
  cpu.io.cen := true.B
  cpu.io.dtack := false.B
  cpu.io.din := 0.U

  // Trigger an interrupt request on the rising edge of the vertical blank signal
  withClockAndReset(io.cpuClock, io.cpuReset) {
    val vBlank = ShiftRegister(io.video.vBlank, 2)
    val iplReg = RegInit(0.U)
    when(Util.rising(vBlank)) { iplReg := 1.U }.elsewhen(clearIRQ) { iplReg := 0.U }
    cpu.io.ipl := iplReg
  }

  // Main RAM
  val mainRam = withClockAndReset(io.cpuClock, io.cpuReset) {
    Module(new SinglePortRam(
      addrWidth = Config.MAIN_RAM_ADDR_WIDTH,
      dataWidth = Config.MAIN_RAM_DATA_WIDTH
    ))
  }

  // Sprite RAM
  val spriteRam = withClockAndReset(io.cpuClock, io.cpuReset) {
    Module(new TrueDualPortRam(
      addrWidthA = Config.SPRITE_RAM_ADDR_WIDTH,
      dataWidthA = Config.SPRITE_RAM_DATA_WIDTH,
      addrWidthB = Config.SPRITE_RAM_GPU_ADDR_WIDTH,
      dataWidthB = Config.SPRITE_RAM_GPU_DATA_WIDTH
    ))
  }
  spriteRam.io.clockB := clock

  // Layer 0 RAM
  val layer0Ram = withClockAndReset(io.cpuClock, io.cpuReset) {
    Module(new TrueDualPortRam(
      addrWidthA = Config.LAYER_0_RAM_ADDR_WIDTH,
      dataWidthA = Config.LAYER_0_RAM_DATA_WIDTH,
      addrWidthB = Config.LAYER_0_RAM_GPU_ADDR_WIDTH,
      dataWidthB = Config.LAYER_0_RAM_GPU_DATA_WIDTH
    ))
  }
  layer0Ram.io.clockB := clock

  // Layer 1 RAM
  val layer1Ram = withClockAndReset(io.cpuClock, io.cpuReset) {
    Module(new TrueDualPortRam(
      addrWidthA = Config.LAYER_1_RAM_ADDR_WIDTH,
      dataWidthA = Config.LAYER_1_RAM_DATA_WIDTH,
      addrWidthB = Config.LAYER_1_RAM_GPU_ADDR_WIDTH,
      dataWidthB = Config.LAYER_1_RAM_GPU_DATA_WIDTH
    ))
  }
  layer1Ram.io.clockB := clock

  // Layer 2 RAM
  //
  // The layer 2 RAM masks address bits 14 and 15 on the CPU-side (i.e. the RAM is 8KB mirrored to 64KB).
  //
  // https://github.com/mamedev/mame/blob/master/src/mame/drivers/cave.cpp#L495
  val layer2Ram = withClockAndReset(io.cpuClock, io.cpuReset) {
    Module(new TrueDualPortRam(
      addrWidthA = Config.LAYER_2_RAM_ADDR_WIDTH,
      dataWidthA = Config.LAYER_2_RAM_DATA_WIDTH,
      addrWidthB = Config.LAYER_2_RAM_GPU_ADDR_WIDTH,
      dataWidthB = Config.LAYER_2_RAM_GPU_DATA_WIDTH
    ))
  }
  layer2Ram.io.clockB := clock

  // Palette RAM
  val paletteRam = withClockAndReset(io.cpuClock, io.cpuReset) {
    Module(new TrueDualPortRam(
      addrWidthA = Config.PALETTE_RAM_ADDR_WIDTH,
      dataWidthA = Config.PALETTE_RAM_DATA_WIDTH,
      addrWidthB = Config.PALETTE_RAM_GPU_ADDR_WIDTH,
      dataWidthB = Config.PALETTE_RAM_GPU_DATA_WIDTH
    ))
  }
  paletteRam.io.clockB := clock

  // Layer registers
  val layer0Regs = withClockAndReset(io.cpuClock, io.cpuReset) { Module(new RegisterFile(Config.LAYER_NUM_REGS)) }
  val layer1Regs = withClockAndReset(io.cpuClock, io.cpuReset) { Module(new RegisterFile(Config.LAYER_NUM_REGS)) }
  val layer2Regs = withClockAndReset(io.cpuClock, io.cpuReset) { Module(new RegisterFile(Config.LAYER_NUM_REGS)) }

  // Video registers
  val videoRegs = withClockAndReset(io.cpuClock, io.cpuReset) { Module(new RegisterFile(Config.VIDEO_NUM_REGS)) }

  // Sound registers
  val soundRegs = withClockAndReset(io.cpuClock, io.cpuReset) { Module(new RegisterFile(Config.SOUND_NUM_REGS)) }

  // GPU
  val gpu = Module(new GPU)
  gpu.io.generateFrame := Util.rising(ShiftRegister(generateFrame, 2))
  gpu.io.videoRegs := videoRegs.io.regs.asUInt
  gpu.io.layer0Regs := layer0Regs.io.regs.asUInt
  gpu.io.layer1Regs := layer1Regs.io.regs.asUInt
  gpu.io.layer2Regs := layer2Regs.io.regs.asUInt
  gpu.io.tileRom <> io.tileRom
  gpu.io.spriteRam <> spriteRam.io.portB
  gpu.io.layer0Ram <> layer0Ram.io.portB
  gpu.io.layer1Ram <> layer1Ram.io.portB
  gpu.io.layer2Ram <> layer2Ram.io.portB
  gpu.io.paletteRam <> paletteRam.io.portB
  gpu.io.frameBuffer <> io.frameBuffer

  // Memory map
  withClockAndReset(io.cpuClock, io.cpuReset) {
    // Program ROM
    cpu.memMap(0x000000 to 0x0fffff).readMemT(io.progRom) { _ + Config.PROG_ROM_OFFSET.U }
    // Main RAM
    cpu.memMap(0x100000 to 0x10ffff).readWriteMem(mainRam.io)
    // Sound regs
    cpu.memMap(0x300000 to 0x300003).writeMem(soundRegs.io.mem.asWriteMemIO)
    // Sprite RAM
    cpu.memMap(0x400000 to 0x40ffff).readWriteMem(spriteRam.io.portA)
    // Layer RAM
    cpu.memMap(0x500000 to 0x507fff).readWriteMem(layer0Ram.io.portA)
    cpu.memMap(0x600000 to 0x607fff).readWriteMem(layer1Ram.io.portA)
    cpu.memMap(0x700000 to 0x70ffff).readWriteMem(layer2Ram.io.portA)
    // Video regs
    cpu.memMap(0x800000 to 0x80007f).writeMem(videoRegs.io.mem.asWriteMemIO)
    // IRQ
    cpu.memMap(0x800000 to 0x800007).r { (addr, _) =>
      when(addr === 0x800004.U) { clearIRQ := true.B }
      3.U
    }
    // Trigger the start of a new frame
    cpu.memMap(0x800004).w { (_, _, data) => generateFrame := data === 0x01f0.U }
    // Layer regs
    cpu.memMap(0x900000 to 0x900005).readWriteMem(layer0Regs.io.mem)
    cpu.memMap(0xa00000 to 0xa00005).readWriteMem(layer1Regs.io.mem)
    cpu.memMap(0xb00000 to 0xb00005).readWriteMem(layer2Regs.io.mem)
    // Palette RAM
    cpu.memMap(0xc00000 to 0xc0ffff).readWriteMem(paletteRam.io.portA)
    // Player 1 inputs
    cpu.memMap(0xd00000 to 0xd00001).r { (_, _) => "b1111111".U ## ~io.player.player1 }
    // Player 2 inputs
    cpu.memMap(0xd00002 to 0xd00003).r { (_, _) => "b1111011".U ## ~io.player.player2 }
    // EEPROM
    cpu.memMap(0xe00000).w { (_, _, _) => /* TODO */ }
    // Access to 0x5fxxxx appears in DoDonPachi on attract loop when showing the air stage on frame 9355 (i.e. after
    // roughly 2 min 30 sec). The game is accessing data relative to a Layer 1 address and underflows. These accesses do
    // nothing, but should be acknowledged in order not to block the CPU.
    //
    // The reason these accesses appear is probably because it made the layer update routine simpler to write (no need
    // to handle edge cases). These accesses are simply ignored by the hardware.
    cpu.memMap(0x5f0000 to 0x5fffff).ignore()
    // Acknowledge accesses outside the M680000 memory range
    // TODO: This hack shouldn't be required by the CPU, but there is something wrong with the CPU implementation.
    when(cpu.io.addr(31, 28) === 0xf.U && cpu.io.rw) { cpu.io.dtack := true.B }
  }

  // Outputs
  io.tileRom.addr := gpu.io.tileRom.addr + Config.TILE_ROM_OFFSET.U
  io.debug.pc := cpu.io.debug.pc
  io.debug.pcw := cpu.io.debug.pcw
}
