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

package arcadia.util

import scala.util.matching.Regex.Match

abstract class Token {
  /** Returns true if the token is not empty, false otherwise. */
  def nonEmpty: Boolean

  /** Returns the length of the token. */
  def length: Int
}

case class StringToken(text: String) extends Token {
  def nonEmpty: Boolean = text.nonEmpty
  def length = text.length
}

case class ArgumentToken(index: Int, width: Option[Int], zero: Boolean) extends Token {
  def nonEmpty: Boolean = width.exists(_ > 0)
  def length = width.getOrElse(0)
}

case class NewlineToken() extends Token {
  def nonEmpty: Boolean = true
  def length = 0
}

object Format {
  /** Argument regular expression */
  val ARG_REGEX = raw"%(0)?(\d)*([Xx])".r

  /**
   * Tokenizes a format string.
   *
   * @param s The format string.
   * @return A list of tokens.
   */
  def tokenize(s: String): Seq[Token] = {
    val strings = ARG_REGEX.split(s).map(parseString).toList
    val args = ARG_REGEX.findAllMatchIn(s).zipWithIndex.map(parseArgument).toList
    strings
      .zipAll(args, None, None)
      .flatMap(x => List(x._1, x._2).flatten)
      .flatMap {
        case t: StringToken => splitStringToken(t)
        case t => List(t)
      }
  }

  /**
   * Intersperses a list of values with a separator.
   *
   * @param x The separator.
   * @param xs The list.
   * @return An interspersed list.
   */
  def intersperse[T](x: T, xs: Seq[T]): Seq[T] = (x, xs) match {
    case (_, Nil) => Nil
    case (_, Seq(x)) => Seq(x)
    case (sep, y :: ys) => y +: sep +: intersperse(sep, ys)
  }

  /**
   * Partitions a list, creating a new sublist every time a predicate is satisfied.
   *
   * The values that satisfy the predicate are not included in the sublist.
   *
   * @param xs The list.
   * @param p The predicate on which to partition.
   * @return A list of sublists.
   */
  def partitionWhen[T](xs: List[T])(p: T => Boolean): List[List[T]] = {
    xs.foldLeft(List.empty[List[T]]) {
      case (y :: ys, x) if p(x) => List.empty :: (y :: ys)
      case (y :: ys, x) => (x :: y) :: ys
      case (ys, x) if p(x) => ys
      case (ys, x) => List(x) :: ys
    }.filter(_.nonEmpty).map(_.reverse).reverse
  }

  private def splitStringToken(token: StringToken): Seq[Token] = {
    val stringTokens = token.text.linesIterator.map(s => StringToken(s)).toSeq
    intersperse[Token](NewlineToken(), stringTokens).filter(_.nonEmpty)
  }

  private def parseString(s: String): Option[StringToken] = {
    if (s.nonEmpty) Some(StringToken(s)) else None
  }

  private def parseArgument(p: (Match, Int)): Option[ArgumentToken] = {
    val List(zero, width, _) = p._1.subgroups.map(Option.apply)
    val token = ArgumentToken(
      index = p._2,
      width = width.map(_.toInt),
      zero = zero.isDefined
    )
    Some(token)
  }
}
