package com.tonapps.perps.trade

import com.tonapps.chainkit.core.chain.model.account.WalletAuth
import com.tonapps.chainkit.core.chain.model.account.WalletKeyPair
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

internal object PerpsAccountProofs {

    private const val BIND_DOMAIN = "perps.account.bind.v1"
    private const val DOMAIN_NAME = "TonkeeperPerps"
    private const val DOMAIN_VERSION = "1"
    private const val DOMAIN_CHAIN_ID = 1
    private const val HEX_PREFIX = "0x"
    private const val MAX_FIELD_SIZE = 255
    private const val SIGNATURE_HEX_LENGTH = 130
    private const val ADDRESS_HEX_LENGTH = 40

    // Every proof below is signed with the wallet's root key, which each one wipes once it has signed.

    /** The wallet's consent to being bound to [l1Address]. */
    fun bindProof(keyPair: WalletKeyPair, walletId: String, l1Address: String): String {
        return sign(keyPair, lp(BIND_DOMAIN.toByteArray()) + lp(walletId.toByteArray()) + lp(l1Address.toByteArray()))
    }

    fun linkWalletTypedData(walletId: String, deadline: Long): String {
        return buildJsonObject {
            putJsonObject("types") {
                putJsonArray("EIP712Domain") {
                    add(typedDataField("name", "string"))
                    add(typedDataField("version", "string"))
                    add(typedDataField("chainId", "uint256"))
                }
                putJsonArray("LinkWallet") {
                    add(typedDataField("walletId", "string"))
                    add(typedDataField("deadline", "uint64"))
                }
            }

            put("primaryType", "LinkWallet")

            putJsonObject("domain") {
                put("name", DOMAIN_NAME)
                put("version", DOMAIN_VERSION)
                put("chainId", DOMAIN_CHAIN_ID)
            }

            putJsonObject("message") {
                put("walletId", walletId)
                put("deadline", deadline)
            }
        }.toString()
    }

    fun normalizeHex(value: String, withPrefix: Boolean): String {
        val body = value.lowercase().removePrefix(HEX_PREFIX)
        require(body.length == SIGNATURE_HEX_LENGTH && isHex(body)) {
            "perps: malformed signature of ${body.length} characters"
        }

        return if (withPrefix) {
            HEX_PREFIX + body
        } else {
            body
        }
    }

    fun normalizeL1AddressOrNull(value: String): String? {
        return try {
            normalizeL1Address(value)
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    fun normalizeL1Address(value: String): String {
        val body = value.lowercase().removePrefix(HEX_PREFIX)
        require(body.length == ADDRESS_HEX_LENGTH && isHex(body)) {
            "perps: malformed L1 address of ${body.length} characters"
        }

        return HEX_PREFIX + body
    }

    private fun sign(keyPair: WalletKeyPair, message: ByteArray): String {
        return try {
            normalizeHex(WalletAuth().signProof(keyPair, message).signature, withPrefix = false)
        } finally {
            keyPair.privateKey.fill(0)
        }
    }

    private fun lp(value: ByteArray): ByteArray {
        require(value.size <= MAX_FIELD_SIZE) {
            "perps: proof field of ${value.size} bytes does not fit a length prefix"
        }

        return byteArrayOf(value.size.toByte()) + value
    }

    private fun typedDataField(name: String, type: String): JsonObject {
        return buildJsonObject {
            put("name", name)
            put("type", type)
        }
    }

    private fun isHex(value: String): Boolean {
        return value.all { it in '0'..'9' || it in 'a'..'f' }
    }
}
