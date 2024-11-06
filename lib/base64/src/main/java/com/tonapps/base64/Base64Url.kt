/*
 * Copyright 2022 Sascha Peilicke <sascha@peilicke.de>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.tonapps.base64

/**
 * Encode a [String] to Base64 URL-safe encoded [String].
 *
 * See [RFC 4648 §5](https://datatracker.ietf.org/doc/html/rfc4648#section-5)
 */
val String.base64UrlEncoded: String
    get() = encodeInternal(Encoding.UrlSafe)

/**
 * Encode a [ByteArray] to Base64 URL-safe encoded [String].
 *
 * See [RFC 4648 §5](https://datatracker.ietf.org/doc/html/rfc4648#section-5)
 */
val ByteArray.base64UrlEncoded: String
    get() = asCharArray().concatToString().base64UrlEncoded

/**
 * Decode a Base64 URL-safe encoded [String] to [String].
 *
 * See [RFC 4648 §5](https://datatracker.ietf.org/doc/html/rfc4648#section-5)
 */
val String.base64UrlDecoded: String
    get() {
        val ret = decodeInternal(Encoding.UrlSafe).map { it.toChar() }
        val foo = ret.joinToString("")
        val bar = foo.dropLast(count { it == '=' })
        return bar.filterNot { it.code == 0 }
    }

/**
 * Decode a Base64 URL-safe encoded [String] to [ByteArray].
 *
 * See [RFC 4648 §5](https://datatracker.ietf.org/doc/html/rfc4648#section-5)
 */
val String.base64UrlDecodedBytes: ByteArray
    get() = decodeInternal(Encoding.UrlSafe)
        .map { it.toByte() }.toList().dropLast(count { it == '=' || it == Char.MIN_VALUE }).toByteArray()

/**
 * Decode a Base64 URL-safe encoded [ByteArray] to [String].
 *
 * See [RFC 4648 §5](https://datatracker.ietf.org/doc/html/rfc4648#section-5)
 */
val ByteArray.base64UrlDecoded: String
    get() = asCharArray().concatToString().base64UrlDecoded