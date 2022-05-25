package axon.mem

import axon.util.Counter
import chisel3._
import chisel3.util._

/**
 * Buffers data written to the input port, until there is enough data to trigger a burst on the
 * output port.
 *
 * @param inAddrWidth  The width of the input address bus.
 * @param inDataWidth  The width of the input data bus.
 * @param outAddrWidth The width of the output address bus.
 * @param outDataWidth The width of the output data bus.
 * @param burstLength  The number of words to transfer during a burst.
 */
class BurstBuffer(inAddrWidth: Int,
                  inDataWidth: Int,
                  outAddrWidth: Int,
                  outDataWidth: Int,
                  burstLength: Int) extends Module {
  val io = IO(new Bundle {
    /** Input port */
    val in = Flipped(AsyncWriteMemIO(inAddrWidth, inDataWidth))
    /** Output port */
    val out = BurstWriteMemIO(outAddrWidth, outDataWidth)
  })

  /** The number of input words in a cache line */
  val inWords = outDataWidth * burstLength / inDataWidth
  /** The number of bytes in an input word */
  val inBytes = inDataWidth / 8
  /** The number of bytes in an output word */
  val outBytes = outDataWidth / 8

  // Registers
  val writePendingReg = RegInit(false.B)
  val lineReg = Reg(new Line(inDataWidth, outDataWidth, burstLength, true))
  val addrReg = Reg(UInt(inAddrWidth.W))

  // Control signals
  val latch = io.in.wr && !writePendingReg
  val effectiveWrite = writePendingReg && !io.out.waitReq

  // Counters
  val (wordCounter, wordCounterWrap) = Counter.static(inWords, enable = latch)
  val (burstCounter, burstCounterWrap) = Counter.static(burstLength, enable = effectiveWrite)

  // Toggle write pending register
  when(io.out.burstDone) {
    writePendingReg := false.B
  }.elsewhen(wordCounterWrap) {
    writePendingReg := true.B
  }

  // Latch input words
  when(latch) {
    val words = WireInit(lineReg.inWords)
    words(wordCounter) := io.in.din
    lineReg.words := words.asTypeOf(chiselTypeOf(lineReg.words))
    addrReg := io.in.addr
  }

  // Outputs
  io.in.waitReq := writePendingReg
  io.out.wr := writePendingReg
  io.out.burstLength := burstLength.U
  io.out.addr := (addrReg >> log2Ceil(outBytes)) << log2Ceil(outBytes)
  io.out.din := lineReg.outWords(burstCounter)
  io.out.mask := Fill(outBytes, 1.U)

  printf(p"(busy: $writePendingReg, addr: ${ io.out.addr }, wordCounter: $wordCounter ($wordCounterWrap), burstCounter: $burstCounter ($burstCounterWrap), line: 0x${ Hexadecimal(lineReg.words.asUInt) })\n")
}
