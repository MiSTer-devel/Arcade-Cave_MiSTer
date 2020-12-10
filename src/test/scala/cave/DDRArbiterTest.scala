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

import chisel3._
import chiseltest._
import org.scalatest._

trait DDRArbiterHelpers {
  protected def waitForIdle(dut: DDRArbiter) =
    while(!dut.io.debug.idle.peek().litToBoolean) { dut.clock.step() }

  protected def waitForProgRomReq(dut: DDRArbiter) =
    while(!dut.io.debug.progRomReq.peek().litToBoolean) { dut.clock.step() }

  protected def waitForProgRomWait(dut: DDRArbiter) =
    while(!dut.io.debug.progRomWait.peek().litToBoolean) { dut.clock.step() }

  protected def waitForSoundRomReq(dut: DDRArbiter) =
    while(!dut.io.debug.soundRomReq.peek().litToBoolean) { dut.clock.step() }

  protected def waitForSoundRomWait(dut: DDRArbiter) =
    while(!dut.io.debug.soundRomWait.peek().litToBoolean) { dut.clock.step() }

  protected def waitForTileRomReq(dut: DDRArbiter) =
    while(!dut.io.debug.tileRomReq.peek().litToBoolean) { dut.clock.step() }

  protected def waitForTileRomWait(dut: DDRArbiter) =
    while(!dut.io.debug.tileRomWait.peek().litToBoolean) { dut.clock.step() }

  protected def waitForFbToDDR(dut: DDRArbiter) =
    while(!dut.io.debug.fbToDDR.peek().litToBoolean) { dut.clock.step() }

  protected def waitForFbFromDDR(dut: DDRArbiter) =
    while(!dut.io.debug.fbFromDDR.peek().litToBoolean) { dut.clock.step() }

  protected def waitForDownload(dut: DDRArbiter) =
    while(!dut.io.debug.download.peek().litToBoolean) { dut.clock.step() }
}

class DDRArbiterTest extends FlatSpec with ChiselScalatestTester with Matchers with DDRArbiterHelpers {
  behavior of "FSM"

  it should "move to the check state" in {
    test(new DDRArbiter) { dut =>
      waitForIdle(dut)
      dut.clock.step()
      dut.io.debug.check1.expect(true.B)
    }
  }

  it should "move to the cache request state" in {
    test(new DDRArbiter) { dut =>
      dut.io.progRom.rd.poke(true.B)
      waitForIdle(dut)
      dut.clock.step(2)
      dut.io.debug.progRomReq.expect(true.B)
    }
  }

  it should "move to the cache wait state" in {
    test(new DDRArbiter) { dut =>
      dut.io.progRom.rd.poke(true.B)
      waitForIdle(dut)
      dut.clock.step(3)
      dut.io.debug.progRomWait.expect(true.B)
    }
  }

  it should "move to the graphics request state" in {
    test(new DDRArbiter) { dut =>
      dut.io.tileRom.rd.poke(true.B)
      waitForIdle(dut)
      dut.clock.step(2)
      dut.io.debug.tileRomReq.expect(true.B)
    }
  }

  it should "move to the graphics wait state" in {
    test(new DDRArbiter) { dut =>
      dut.io.tileRom.rd.poke(true.B)
      waitForIdle(dut)
      dut.clock.step(3)
      dut.io.debug.tileRomWait.expect(true.B)
    }
  }

  it should "return to the idle state after reading a cache line" in {
    test(new DDRArbiter) { dut =>
      dut.io.progRom.rd.poke(true.B)
      waitForProgRomWait(dut)
      dut.io.ddr.burstDone.poke(true.B)
      dut.clock.step()
      dut.io.debug.idle.expect(true.B)
    }
  }

  it should "return to the idle state after reading graphics data" in {
    test(new DDRArbiter) { dut =>
      dut.io.tileRom.rd.poke(true.B)
      waitForTileRomWait(dut)
      dut.io.ddr.burstDone.poke(true.B)
      dut.clock.step()
      dut.io.debug.idle.expect(true.B)
    }
  }

  it should "return to the idle state after writing the frame buffer to DDR" in {
    test(new DDRArbiter) { dut =>
      dut.io.fbToDDR.wr.poke(true.B)
      waitForFbToDDR(dut)
      dut.io.ddr.burstDone.poke(true.B)
      dut.clock.step()
      dut.io.debug.idle.expect(true.B)
    }
  }

  it should "return to the idle state after reading the frame buffer from DDR" in {
    test(new DDRArbiter) { dut =>
      dut.io.fbFromDDR.rd.poke(true.B)
      waitForFbFromDDR(dut)
      dut.io.ddr.burstDone.poke(true.B)
      dut.clock.step()
      dut.io.debug.idle.expect(true.B)
    }
  }

  it should "move to the download state" in {
    test(new DDRArbiter) { dut =>
      dut.io.download.cs.poke(true.B)
      waitForIdle(dut)
      dut.clock.step(2)
      dut.io.debug.download.expect(true.B)
    }
  }

  behavior of "program ROM"

  it should "read a cache line from DDR" in {
    test(new DDRArbiter) { dut =>
      dut.io.progRom.rd.poke(true.B)
      dut.io.progRom.addr.poke(1.U)
      waitForProgRomReq(dut)
      dut.io.ddr.rd.expect(true.B)
      dut.io.ddr.addr.expect(1.U)
      waitForProgRomWait(dut)
      dut.io.ddr.rd.expect(false.B)
      0.to(3).foreach { n =>
        dut.io.ddr.valid.poke(true.B)
        dut.io.ddr.dout.poke(n.U)
        dut.io.progRom.valid.expect(true.B)
        dut.io.progRom.dout.expect(n.U)
        dut.clock.step()
      }
      dut.io.ddr.burstDone.poke(true.B)
      dut.io.progRom.burstDone.expect(true.B)
    }
  }

  behavior of "sound ROM"

  it should "read a cache line from DDR" in {
    test(new DDRArbiter) { dut =>
      dut.io.soundRom.rd.poke(true.B)
      dut.io.soundRom.addr.poke(1.U)
      waitForSoundRomReq(dut)
      dut.io.ddr.rd.expect(true.B)
      dut.io.ddr.addr.expect(1.U)
      waitForSoundRomWait(dut)
      dut.io.ddr.rd.expect(false.B)
      0.to(3).foreach { n =>
        dut.io.ddr.valid.poke(true.B)
        dut.io.ddr.dout.poke(n.U)
        dut.io.soundRom.valid.expect(true.B)
        dut.io.soundRom.dout.expect(n.U)
        dut.clock.step()
      }
      dut.io.ddr.burstDone.poke(true.B)
      dut.io.soundRom.burstDone.expect(true.B)
    }
  }

  behavior of "tile ROM"

  it should "read a large tile from DDR" in {
    test(new DDRArbiter) { dut =>
      dut.io.tileRom.rd.poke(true.B)
      dut.io.tileRom.burstLength.poke(16.U)
      dut.io.tileRom.addr.poke(1.U)
      waitForTileRomReq(dut)
      dut.io.ddr.rd.expect(true.B)
      dut.io.ddr.addr.expect(1.U)
      waitForTileRomWait(dut)
      dut.io.ddr.rd.expect(false.B)
      0.to(15).foreach { n =>
        dut.io.ddr.valid.poke(true.B)
        dut.io.ddr.dout.poke(n.U)
        dut.io.tileRom.valid.expect(true.B)
        dut.io.tileRom.dout.expect(n.U)
        dut.clock.step()
      }
      dut.io.ddr.burstDone.poke(true.B)
      dut.io.tileRom.burstDone.expect(true.B)
    }
  }

  it should "read a small tile from DDR" in {
    test(new DDRArbiter) { dut =>
      dut.io.tileRom.rd.poke(true.B)
      dut.io.tileRom.burstLength.poke(8.U)
      dut.io.tileRom.addr.poke(1.U)
      waitForTileRomReq(dut)
      dut.io.ddr.rd.expect(true.B)
      dut.io.ddr.addr.expect(1.U)
      waitForTileRomWait(dut)
      dut.io.ddr.rd.expect(false.B)
      0.to(7).foreach { n =>
        dut.io.ddr.valid.poke(true.B)
        dut.io.ddr.dout.poke(n.U)
        dut.io.tileRom.valid.expect(true.B)
        dut.io.tileRom.dout.expect(n.U)
        dut.clock.step()
      }
      dut.io.ddr.burstDone.poke(true.B)
      dut.io.tileRom.burstDone.expect(true.B)
    }
  }

  behavior of "frame buffer"

  it should "read the frame buffer from DDR" in {
    test(new DDRArbiter) { dut =>
      dut.io.fbFromDDR.rd.poke(true.B)
      dut.io.fbFromDDR.addr.poke(1.U)
      dut.io.fbFromDDR.burstLength.poke(4.U)
      waitForFbFromDDR(dut)
      dut.io.ddr.rd.expect(true.B)
      dut.io.ddr.addr.expect(1.U)
      0.to(3).foreach { n =>
        dut.io.ddr.valid.poke(true.B)
        dut.io.ddr.dout.poke(n.U)
        dut.io.fbFromDDR.valid.expect(true.B)
        dut.io.fbFromDDR.dout.expect(n.U)
        dut.clock.step()
      }
      dut.io.fbFromDDR.rd.poke(false.B)
      dut.io.ddr.rd.expect(false.B)
    }
  }

  it should "write the frame buffer to DDR" in {
    test(new DDRArbiter) { dut =>
      dut.io.fbToDDR.wr.poke(true.B)
      dut.io.fbToDDR.addr.poke(1.U)
      dut.io.fbToDDR.mask.poke(0xff.U)
      dut.io.fbToDDR.burstLength.poke(4.U)
      waitForFbToDDR(dut)
      dut.io.ddr.wr.expect(true.B)
      dut.io.ddr.addr.expect(1.U)
      dut.io.ddr.mask.expect(0xff.U)
      0.to(3).foreach { n =>
        dut.io.fbToDDR.din.poke(n.U)
        dut.io.ddr.din.expect(n.U)
        dut.clock.step()
      }
      dut.io.fbToDDR.wr.poke(false.B)
      dut.io.ddr.wr.expect(false.B)
    }
  }

  behavior of "download"

  it should "write DDR data" in {
    test(new DDRArbiter) { dut =>
      dut.io.download.cs.poke(true.B)
      waitForDownload(dut)
      dut.io.ddr.wr.expect(false.B)
      dut.io.download.wr.poke(true.B)
      dut.io.download.dout.poke(0x1234.U)
      dut.io.ddr.wr.expect(true.B)
      dut.io.ddr.din.expect(0x1234123412341234L.U)
    }
  }

  it should "set DDR address and byte mask" in {
    test(new DDRArbiter) { dut =>
      dut.io.download.cs.poke(true.B)
      dut.io.download.wr.poke(true.B)
      waitForDownload(dut)
      dut.io.download.addr.poke(0.U)
      dut.io.ddr.addr.expect(0.U)
      dut.io.ddr.mask.expect(0x03.U)
      dut.clock.step()
      dut.io.download.addr.poke(2.U)
      dut.io.ddr.addr.expect(2.U)
      dut.io.ddr.mask.expect(0x0c.U)
      dut.clock.step()
      dut.io.download.addr.poke(4.U)
      dut.io.ddr.addr.expect(4.U)
      dut.io.ddr.mask.expect(0x30.U)
      dut.clock.step()
      dut.io.download.addr.poke(6.U)
      dut.io.ddr.addr.expect(6.U)
      dut.io.ddr.mask.expect(0xc0.U)
      dut.clock.step()
      dut.io.download.addr.poke(8.U)
      dut.io.ddr.addr.expect(8.U)
      dut.io.ddr.mask.expect(0x03.U)
    }
  }
}
