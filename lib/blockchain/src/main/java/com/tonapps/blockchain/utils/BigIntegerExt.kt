@file:OptIn(ExperimentalTypeInference::class)

package com.tonapps.blockchain.utils

import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import kotlin.experimental.ExperimentalTypeInference

fun BigInteger.toPaddedHexString(): String {
    var hexString: String = toString(16)
    if (hexString.length % 2 != 0) {
        hexString = "0$hexString"
    }
    return "0x$hexString"
}

fun BigInteger.toByteString(): ByteString = toByteArray().toByteString()

fun BigInteger.toBigDecimal(decimalMode: MathContext? = null): BigDecimal =
    BigDecimal(this, decimalMode)

fun BigInteger.multiplyBy(factor: BigDecimal): BigInteger = (this.toBigDecimal() * factor).toBigInteger()

@OverloadResolutionByLambdaReturnType
@JvmName("sumOfBigInteger")
inline fun <T> Iterable<T>.sumOf(selector: (T) -> BigInteger): BigInteger =
    fold(BigInteger.ZERO) { acc, t -> acc + selector(t) }

@OverloadResolutionByLambdaReturnType
@JvmName("sumOfBigDecimal")
inline fun <T> Iterable<T>.sumOf(selector: (T) -> BigDecimal): BigDecimal =
    fold(BigDecimal.ZERO) { acc, t -> acc + selector(t) }

fun BigInteger.hex(): String =
    toString(16)

fun BigInteger.hexWithPrefix(): String =
    toString(16).add0x()

fun BigInteger.toValue(decimals: Int): BigDecimal =
    this.toBigDecimal().divide(BigDecimal.TEN.pow(decimals))

fun BigDecimal.toUnit(decimals: Int): BigInteger =
    this.multiply(BigDecimal.TEN.pow(decimals)).toBigInteger()

fun BigInteger.toUnsignedByteArray(): ByteArray {
    require(this >= BigInteger.ZERO) { error("Should be non-negative") }
    val bytes = this.toByteArray()
    val startIndex = if (bytes[0] == 0.toByte()) 1 else 0
    return bytes.copyOfRange(startIndex, bytes.size)
}
