package com.tonapps.blockchain.evm

import com.tonapps.blockchain.utils.HexPrefix
import com.tonapps.chainkit.core.chain.model.account.Chain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException

private const val TYPES = "types"
private const val DOMAIN = "domain"
private const val DOMAIN_TYPE = "EIP712Domain"
private const val FIELD_NAME = "name"
private const val CHAIN_ID = "chainId"
private const val UINT256_BITS = 256

class EvmTypedData private constructor(
    val json: String,
    val chainId: BigInteger?,
) {

    fun isAddressedTo(chain: Chain): Boolean {
        return chainId == null || chainId.toString() == chain.id
    }

    companion object {

        fun decode(raw: String): EvmTypedData? {
            val text = when (raw.startsWith(HexPrefix)) {
                true -> utf8FromHex(raw.removePrefix(HexPrefix)) ?: return null
                false -> raw
            }
            val json = try {
                Json.parseToJsonElement(text) as? JsonObject
            } catch (_: IllegalArgumentException) {
                null
            }
            return json?.let(::of)
        }

        fun of(json: JsonObject): EvmTypedData? {
            val domain = when (val element = json[DOMAIN]) {
                null -> null
                is JsonObject -> element
                else -> return null
            }
            val declared = domain?.get(CHAIN_ID)?.takeUnless { it is JsonNull }
            val chainId = when {
                declared != null -> (declared as? JsonPrimitive)?.let(::uint256) ?: return null
                json.declaresDomainChainId() -> return null
                else -> null
            }
            return EvmTypedData(json = json.toString(), chainId = chainId)
        }

        private fun uint256(value: JsonPrimitive): BigInteger? {
            val content = value.content
            val number = when {
                value.isString && content.startsWith(HexPrefix) -> {
                    content.removePrefix(HexPrefix)
                        .takeIf { it.isNotEmpty() && it.all(::isHexDigit) }
                        ?.let { BigInteger(it, 16) }
                }
                content.isCanonicalDecimal() -> BigInteger(content)
                else -> null
            }
            return number?.takeIf { it.bitLength() <= UINT256_BITS }
        }

        private fun JsonObject.declaresDomainChainId(): Boolean {
            val fields = (this[TYPES] as? JsonObject)?.get(DOMAIN_TYPE) as? JsonArray ?: return false
            return fields.any { field ->
                ((field as? JsonObject)?.get(FIELD_NAME) as? JsonPrimitive)?.content == CHAIN_ID
            }
        }

        private fun utf8FromHex(hex: String): String? {
            if (hex.length % 2 != 0 || !hex.all(::isHexDigit)) {
                return null
            }
            val bytes = ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
            return try {
                Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString()
            } catch (_: CharacterCodingException) {
                null
            }
        }

        private fun String.isCanonicalDecimal(): Boolean {
            return isNotEmpty() && all { it in '0'..'9' } && (length == 1 || first() != '0')
        }

        private fun isHexDigit(char: Char): Boolean {
            return char in '0'..'9' || char in 'a'..'f' || char in 'A'..'F'
        }
    }
}
