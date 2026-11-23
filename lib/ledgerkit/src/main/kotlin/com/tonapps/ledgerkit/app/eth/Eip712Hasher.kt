package com.tonapps.ledgerkit.app.eth

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.web3j.crypto.Hash
import java.io.ByteArrayOutputStream
import java.math.BigInteger

object Eip712Hasher {

    private const val WORD = 32

    fun domainSeparator(typedData: Eip712TypedData): ByteArray =
        hashStruct(typedData, Eip712TypedData.DOMAIN_TYPE, typedData.domain)

    fun messageHash(typedData: Eip712TypedData): ByteArray =
        hashStruct(typedData, typedData.primaryType, typedData.message)

    fun digest(typedData: Eip712TypedData): ByteArray =
        Hash.sha3(byteArrayOf(0x19, 0x01) + domainSeparator(typedData) + messageHash(typedData))

    fun encodeType(typedData: Eip712TypedData, primaryType: String): String {
        val dependencies = collectDependencies(typedData, primaryType, linkedSetOf()) - primaryType
        return (listOf(primaryType) + dependencies.sorted()).joinToString("") { typeName ->
            val fields = typedData.types[typeName] ?: throw IllegalArgumentException("Type $typeName is not defined")
            fields.joinToString(separator = ",", prefix = "$typeName(", postfix = ")") { "${it.type} ${it.name}" }
        }
    }

    private fun hashStruct(typedData: Eip712TypedData, typeName: String, value: JsonObject): ByteArray {
        val fields = typedData.types[typeName] ?: throw IllegalArgumentException("Type $typeName is not defined")
        val out = ByteArrayOutputStream()
        out.write(Hash.sha3(encodeType(typedData, typeName).toByteArray(Charsets.UTF_8)))
        for (field in fields) {
            val fieldValue = value[field.name] ?: throw IllegalArgumentException("Typed data is missing field ${field.name}")
            out.write(encodeValue(typedData, Eip712TypeDescriptor.parse(field.type), fieldValue))
        }
        return Hash.sha3(out.toByteArray())
    }

    private fun encodeValue(typedData: Eip712TypedData, descriptor: Eip712TypeDescriptor, value: JsonElement): ByteArray {
        if (descriptor.isArray) {
            val items = value.asArrayOrNull() ?: throw IllegalArgumentException("Expected an array")
            val expectedSize = descriptor.outermostLevel
            require(expectedSize == null || expectedSize == items.size) { "Fixed array size mismatch" }
            val element = descriptor.elementDescriptor()
            val out = ByteArrayOutputStream()
            items.forEach { out.write(encodeValue(typedData, element, it)) }
            return Hash.sha3(out.toByteArray())
        }
        if (typedData.types.containsKey(descriptor.baseName) && descriptor.baseName !in Eip712Values.PRIMITIVES) {
            val struct = value.asObjectOrNull() ?: throw IllegalArgumentException("Expected an object for ${descriptor.baseName}")
            return hashStruct(typedData, descriptor.baseName, struct)
        }
        return when (descriptor.baseName) {
            "string" -> Hash.sha3(Eip712Values.text(value).toByteArray(Charsets.UTF_8))
            "bytes" -> {
                val bytes = Eip712Values.hex(value)
                val size = descriptor.baseSize
                if (size == null) {
                    Hash.sha3(bytes)
                } else {
                    require(bytes.size <= size) { "bytes$size value is too long" }
                    bytes.copyOf(WORD)
                }
            }
            "bool" -> word(if (Eip712Values.bool(value)) { BigInteger.ONE } else { BigInteger.ZERO })
            "address" -> {
                val bytes = Eip712Values.hex(value)
                require(bytes.size == 20) { "Invalid address" }
                ByteArray(WORD - bytes.size) + bytes
            }
            "uint" -> {
                val number = Eip712Values.integer(value)
                Eip712Values.requireIntegerRange(number, descriptor.baseSize ?: 256, signed = false)
                word(number)
            }
            "int" -> {
                val number = Eip712Values.integer(value)
                Eip712Values.requireIntegerRange(number, descriptor.baseSize ?: 256, signed = true)
                word(number)
            }
            else -> throw IllegalArgumentException("Unsupported EIP-712 type ${descriptor.baseName}")
        }
    }

    private fun collectDependencies(typedData: Eip712TypedData, typeName: String, found: MutableSet<String>): Set<String> {
        if (typeName in found || typeName in Eip712Values.PRIMITIVES) {
            return found
        }
        val fields = typedData.types[typeName] ?: return found
        found += typeName
        for (field in fields) {
            val baseName = Eip712TypeDescriptor.parse(field.type).baseName
            if (typedData.types.containsKey(baseName)) {
                collectDependencies(typedData, baseName, found)
            }
        }
        return found
    }

    private fun word(number: BigInteger): ByteArray {
        val unsigned = if (number.signum() < 0) { BigInteger.ONE.shiftLeft(WORD * 8).add(number) } else { number }
        val bytes = unsigned.toByteArray()
        val trimmed = if (bytes.size > WORD) { bytes.copyOfRange(bytes.size - WORD, bytes.size) } else { bytes }
        return ByteArray(WORD - trimmed.size) + trimmed
    }

}
