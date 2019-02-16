/*
 * Copyright 2018-2019 OVO Energy Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fs2.kafka

import cats.data.{Chain, NonEmptyChain}
import cats.Show
import fs2.kafka.internal.syntax._
import org.apache.kafka.common.header.internals.RecordHeaders

/**
  * [[Headers]] represent an immutable append-only collection
  * of [[Header]]s. To create a new [[Headers]] instance, you
  * can use [[Headers#apply]] or [[Headers#empty]] and add an
  * instance of [[Header]] using `append`.
  */
sealed abstract class Headers {

  /**
    * Returns the first header with the specified key,
    * wrapped in `Some`, or `None` if no such header
    * exists. Alias for [[withKey]].
    */
  final def apply(key: String): Option[Header] =
    withKey(key)

  /**
    * Returns the first header with the specified key,
    * wrapped in `Some`, or `None` if no such header
    * exists. The [[apply]] function is an alias.
    */
  def withKey(key: String): Option[Header]

  /**
    * Creates a new [[Headers]] instance with the specified
    * [[Header]] included.
    */
  def append(header: Header): Headers

  /**
    * Creates a new [[Headers]] instance including a
    * [[Header]] with the specified key and value.
    */
  def append(key: String, value: Array[Byte]): Headers

  /**
    * Appends the specified [[Headers]] after these headers.
    */
  def concat(that: Headers): Headers

  /** The included [[Header]]s as a `Chain`. */
  def toChain: Chain[Header]

  /** `true` if at least one [[Header]] is included; otherwise `false`. */
  final def nonEmpty: Boolean = !isEmpty

  /** `true` if no [[Header]]s are included; otherwise `false`. */
  def isEmpty: Boolean

  /** The [[Headers]] as an immutable Java Kafka `Headers` instance. */
  def asJava: KafkaHeaders
}

object Headers {
  private[this] final class HeadersImpl(
    val headers: NonEmptyChain[Header]
  ) extends Headers {
    override def withKey(key: String): Option[Header] =
      headers.find(_.key == key)

    override def append(header: Header): Headers =
      new HeadersImpl(headers.append(header))

    override def append(key: String, value: Array[Byte]): Headers =
      append(Header(key, value))

    override def concat(that: Headers): Headers = {
      if (that.isEmpty) this
      else new HeadersImpl(headers.appendChain(that.toChain))
    }

    override def toChain: Chain[Header] =
      headers.toChain

    override val isEmpty: Boolean =
      false

    override def asJava: KafkaHeaders = {
      val headers: Array[KafkaHeader] =
        toChain.iterator.toArray

      val recordHeaders = new RecordHeaders(headers)
      recordHeaders.setReadOnly()
      recordHeaders
    }

    override def toString: String =
      headers.mkStringAppend { (append, header) =>
        append(header.key)
        append(" -> ")
        append(java.util.Arrays.toString(header.value))
      }(
        start = "Headers(",
        sep = ", ",
        end = ")"
      )
  }

  /**
    * Creates a new [[Headers]] instance from the specified
    * [[Header]]s.
    */
  def apply(headers: Header*): Headers =
    if (headers.isEmpty) empty
    else new HeadersImpl(NonEmptyChain.fromChainUnsafe(Chain(headers: _*)))

  /**
    * Creates a new [[Headers]] instance from the specified
    * `Chain` of [[Header]]s.
    */
  def fromChain(headers: Chain[Header]): Headers =
    if (headers.isEmpty) empty
    else new HeadersImpl(NonEmptyChain.fromChainUnsafe(headers))

  /**
    * Creates a new [[Headers]] instance from the specified
    * `Seq` of [[Header]]s.
    */
  def fromSeq(headers: Seq[Header]): Headers =
    fromChain(Chain.fromSeq(headers))

  /**
    * Creates a new [[Headers]] instance from the specified
    * `Iterable` of [[Header]]s.
    */
  def fromIterable(headers: Iterable[Header]): Headers =
    fromSeq(headers.toSeq)

  /** The empty [[Headers]] instance without any [[Header]]s. */
  val empty: Headers =
    new Headers {
      override def withKey(key: String): Option[Header] =
        None

      override def append(header: Header): Headers =
        new HeadersImpl(NonEmptyChain.one(header))

      override def append(key: String, value: Array[Byte]): Headers =
        append(Header(key, value))

      override def concat(that: Headers): Headers =
        that

      override val toChain: Chain[Header] =
        Chain.empty

      override val isEmpty: Boolean =
        true

      override val asJava: KafkaHeaders = {
        val headers = new RecordHeaders()
        headers.setReadOnly()
        headers
      }

      override def toString: String =
        "Headers()"
    }

  implicit val headersShow: Show[Headers] =
    Show.fromToString
}
