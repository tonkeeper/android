package com.tonapps.ledgerkit.util

import java.math.BigInteger
import java.security.MessageDigest

object Base58 {

    private const val ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
    private val BASE = BigInteger.valueOf(58)
    private const val CHECKSUM_LENGTH = 4

    fun encode(input: ByteArray): String {
        var number = BigInteger(1, input)
        val builder = StringBuilder()
        while (number.signum() > 0) {
            val (quotient, remainder) = number.divideAndRemainder(BASE).let { it[0] to it[1] }
            builder.append(ALPHABET[remainder.toInt()])
            number = quotient
        }
        input.takeWhile { it == 0.toByte() }.forEach { _ -> builder.append(ALPHABET[0]) }
        return builder.reverse().toString()
    }

    fun decode(input: String): ByteArray {
        var number = BigInteger.ZERO
        for (char in input) {
            val digit = ALPHABET.indexOf(char)
            require(digit >= 0) { "Invalid Base58 character" }
            number = number.multiply(BASE).add(BigInteger.valueOf(digit.toLong()))
        }
        val bytes = number.toByteArray().let { if (it.size > 1 && it[0] == 0.toByte()) { it.copyOfRange(1, it.size) } else { it }}
        val leadingZeros = input.takeWhile { it == ALPHABET[0] }.length
        val body = if (number.signum() == 0) { ByteArray(0) } else { bytes }
        return ByteArray(leadingZeros) + body
    }

    fun encodeWithChecksum(payload: ByteArray): String = encode(payload + checksum(payload))

    fun decodeWithChecksum(input: String): ByteArray {
        val decoded = decode(input)
        require(decoded.size > CHECKSUM_LENGTH) { "Base58check value is too short" }
        val payload = decoded.copyOfRange(0, decoded.size - CHECKSUM_LENGTH)
        val checksum = decoded.copyOfRange(decoded.size - CHECKSUM_LENGTH, decoded.size)
        require(checksum.contentEquals(checksum(payload))) { "Invalid Base58check checksum" }
        return payload
    }

    private fun checksum(payload: ByteArray): ByteArray {
        val sha = MessageDigest.getInstance("SHA-256")
        return sha.digest(sha.digest(payload)).copyOfRange(0, CHECKSUM_LENGTH)
    }
}
