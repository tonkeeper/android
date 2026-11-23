package com.tonapps.ledgerkit.app.eth

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.booleanOrNull
import java.math.BigInteger

internal object Eip712Values {

    val PRIMITIVES = setOf("int", "uint", "address", "bool", "string", "bytes")

    fun text(value: JsonElement): String {
        val primitive = value.asPrimitiveOrNull()
        require(primitive != null && value !is JsonNull) { "Expected a primitive value in typed data" }
        return primitive.content
    }

    fun bool(value: JsonElement): Boolean {
        val primitive = value.asPrimitiveOrNull() ?: throw IllegalArgumentException("Expected a boolean value in typed data")
        return primitive.booleanOrNull ?: (integer(value).signum() != 0)
    }

    fun integer(value: JsonElement): BigInteger {
        val content = text(value).trim()
        return when {
            content.startsWith("-0x") || content.startsWith("-0X") -> BigInteger(content.substring(3), 16).negate()
            content.startsWith("0x") || content.startsWith("0X") ->
                if (content.length == 2) { BigInteger.ZERO } else { BigInteger(content.substring(2), 16) }
            content.isEmpty() -> BigInteger.ZERO
            content.contains('e') || content.contains('E') || content.contains('.') -> content.toBigDecimal().toBigIntegerExact()
            else -> BigInteger(content)
        }
    }

    fun hex(value: JsonElement): ByteArray {
        val clean = text(value).removePrefix("0x").removePrefix("0X")
        require(clean.length % 2 == 0 && clean.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
            "Invalid hex value in typed data"
        }
        return ByteArray(clean.length / 2) { clean.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }

    fun requireIntegerRange(number: BigInteger, bits: Int, signed: Boolean) {
        if (signed) {
            require(number.bitLength() < bits) { "Integer does not fit into int$bits" }
        } else {
            require(number.signum() >= 0 && number.bitLength() <= bits) { "Integer does not fit into uint$bits" }
        }
    }
}
