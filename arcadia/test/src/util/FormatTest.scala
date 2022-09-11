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

import arcadia.util._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class FormatTest extends AnyFlatSpec with Matchers {
  behavior of "tokenize"

  it should "parse a string" in {
    Format.tokenize("FOO") should be(List(
      StringToken(text = "FOO")
    ))
  }

  it should "parse a hexadecimal argument" in {
    Format.tokenize("%X") should be(List(
      ArgumentToken(index = 0, width = None, zero = false)
    ))
  }

  it should "parse a string followed by a hexadecimal argument" in {
    Format.tokenize("FOO %X") should be(List(
      StringToken(text = "FOO "),
      ArgumentToken(index = 0, width = None, zero = false)
    ))
  }

  it should "parse a hexadecimal argument followed by a string" in {
    Format.tokenize("%X FOO") should be(List(
      ArgumentToken(index = 0, width = None, zero = false),
      StringToken(text = " FOO")
    ))
  }

  it should "parse a hexadecimal argument with width" in {
    Format.tokenize("%4X") should be(List(
      ArgumentToken(index = 0, width = Some(4), zero = false)
    ))
  }

  it should "parse a hexadecimal argument with zero flag" in {
    Format.tokenize("%0X") should be(List(
      ArgumentToken(index = 0, width = None, zero = true)
    ))
  }

  it should "parse multiple lines" in {
    Format.tokenize("FOO %X\nBAR %X") should be(List(
      StringToken(text = "FOO "),
      ArgumentToken(index = 0, width = None, zero = false),
      NewlineToken(),
      StringToken(text = "BAR "),
      ArgumentToken(index = 1, width = None, zero = false)
    ))
  }

  behavior of "partitionWhen"

  it should "partition an empty list" in {
    Format.partitionWhen(List.empty[Integer])(_ == 0) should be(List())
  }

  it should "partition a singleton list" in {
    Format.partitionWhen(List(0))(_ == 0) should be(List())
    Format.partitionWhen(List(1))(_ == 0) should be(List(List(1)))
  }

  it should "partition a list" in {
    Format.partitionWhen(List(0, 1, 0, 2, 3, 0, 4, 0))(_ == 0) should be(List(
      List(1),
      List(2, 3),
      List(4)
    ))
  }
}
