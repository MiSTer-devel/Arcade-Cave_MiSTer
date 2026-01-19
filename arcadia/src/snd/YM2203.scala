package arcadia.snd

import arcadia.clk.ClockDivider
import arcadia.mem._
import chisel3._
import chisel3.util._

/**
 * The YM2203 is a FM sound synthesizer.
 *
 * @param clockFreq  The system clock frequency (Hz).
 * @param sampleFreq The sample clock frequency (Hz).
 * @note This module wraps jotego's JT12 implementation.
 * @see https://github.com/jotego/jt12
 */
class YM2203(clockFreq: Double, sampleFreq: Double) extends Module {
  val io = IO(new Bundle {
    /** CPU port */
    val cpu = Flipped(MemIO(1, 8))
    /** IRQ */
    val irq = Output(Bool())
    /** Audio output port */
    val audio = ValidIO(new Bundle {
      val psg = SInt(16.W)
      val fm = SInt(16.W)
    })
  })

  class JT03_ extends ExtModule {
    val io = FlatIO(new Bundle {
      val rst = Input(Bool())
      val clk = Input(Bool())
      val cen = Input(Bool())
      val din = Input(Bits(8.W))
      val addr = Input(Bool())
      val cs_n = Input(Bool())
      val wr_n = Input(Bool())
      val dout = Output(Bits(8.W))
      val irq_n = Output(Bool())
      val psg_snd = Output(UInt(10.W))
      val fm_snd = Output(SInt(16.W))
      val snd_sample = Output(Bool())
    })

    override def desiredName = "jt03"
  }

  val m = Module(new JT03_)
  m.io.rst := reset.asBool
  m.io.clk := clock.asBool
  m.io.cen := ClockDivider(clockFreq / sampleFreq)
  m.io.cs_n := false.B
  m.io.wr_n := !io.cpu.wr
  m.io.addr := io.cpu.addr(0)
  m.io.din := io.cpu.din
  io.cpu.dout := m.io.dout
  io.irq := !m.io.irq_n
  io.audio.valid := m.io.snd_sample
  io.audio.bits.psg := (0.U ## m.io.psg_snd ## 0.U(5.W)).asSInt
  io.audio.bits.fm := m.io.fm_snd
}
