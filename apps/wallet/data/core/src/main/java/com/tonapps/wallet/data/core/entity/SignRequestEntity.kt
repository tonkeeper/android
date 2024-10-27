package com.tonapps.wallet.data.core.entity

import android.net.Uri
import android.os.Parcelable
import android.util.Log
import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.blockchain.ton.extensions.isValidTonAddress
import com.tonapps.blockchain.ton.extensions.toAccountId
import com.tonapps.extensions.currentTimeSeconds
import kotlinx.datetime.Clock
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import org.json.JSONArray
import org.json.JSONObject
import org.ton.block.AddrStd
import kotlin.time.Duration.Companion.seconds

@Parcelize
data class SignRequestEntity(
    val appUri: Uri,
    private val fromValue: String?,
    private val sourceValue: String?,
    val validUntil: Long,
    val messages: List<RawMessageEntity>,
    val network: TonNetwork
): Parcelable {

    @IgnoredOnParcel
    val from: AddrStd?
        get() {
            val value = fromValue ?: return null
            return AddrStd.parse(value)
        }

    constructor(json: JSONObject, appUri: Uri) : this(
        appUri = appUri,
        fromValue = json.optString("from"),
        sourceValue = json.optString("source"),
        validUntil = parseValidUnit(json),
        messages = parseMessages(json.getJSONArray("messages")),
        network = parseNetwork(json.opt("network"))
    )

    constructor(value: String, appUri: Uri) : this(JSONObject(value), appUri)

    constructor(value: Any, appUri: Uri) : this(value.toString(), appUri)

    class Builder {
        private var from: AddrStd? = null
        private var validUntil: Long? = null
        private var network: TonNetwork = TonNetwork.MAINNET
        private val messages = mutableListOf<RawMessageEntity>()

        fun setFrom(from: AddrStd) = apply { this.from = from }

        fun setValidUntil(validUntil: Long) = apply { this.validUntil = validUntil }

        fun setNetwork(network: TonNetwork) = apply { this.network = network }

        fun setTestnet(testnet: Boolean) = setNetwork(if (testnet) TonNetwork.TESTNET else TonNetwork.MAINNET)

        fun addMessage(message: RawMessageEntity) = apply { messages.add(message) }

        fun build(appUri: Uri): SignRequestEntity {
            return SignRequestEntity(
                fromValue = from?.toAccountId(),
                sourceValue = null,
                validUntil = validUntil ?: 0,
                messages = messages.toList(),
                network = network,
                appUri = appUri
            )
        }
    }

    companion object {

        fun parse(array: JSONArray, appUri: Uri): List<SignRequestEntity> {
            val requests = mutableListOf<SignRequestEntity>()
            for (i in 0 until array.length()) {
                requests.add(SignRequestEntity(array.get(i), appUri))
            }
            return requests.toList()
        }

        private fun parseValidUnit(json: JSONObject): Long {
            val value = json.opt("valid_until") ?: json.opt("validUntil")
            if (value == null) {
                return 0
            }
            val validUnit = when (value) {
                is Long -> value
                is Int -> value.toLong()
                is String -> value.toLongOrNull() ?: throw IllegalArgumentException("Invalid validUntil parameter. Expected: int64 (Like ${currentTimeSeconds()}), Received: $value")
                else -> throw IllegalArgumentException("Invalid validUntil parameter. Expected: int64 (Like ${currentTimeSeconds()}), Received: $value")
            }
            if (validUnit > 1000000000000) {
                return validUnit / 1000
            }
            if (validUnit > 1000000000) {
                return validUnit
            }
            return 0
        }

        private fun parseMessages(array: JSONArray): List<RawMessageEntity> {
            val messages = mutableListOf<RawMessageEntity>()
            for (i in 0 until array.length()) {
                val json = array.getJSONObject(i)
                val raw = RawMessageEntity(json)
                if (0 >= raw.amount) {
                    throw IllegalArgumentException("Invalid amount: ${raw.amount}")
                }
                if (!raw.addressValue.isValidTonAddress()) {
                    throw IllegalArgumentException("Invalid address: ${raw.addressValue}")
                }
                messages.add(raw)
            }
            return messages
        }

        private fun parseNetwork(value: Any?): TonNetwork {
            if (value == null) {
                return TonNetwork.MAINNET
            }
            if (value is String) {
                return parseNetwork(value.toIntOrNull())
            }
            if (value !is Int) {
                return parseNetwork(value.toString())
            }
            return if (value == TonNetwork.TESTNET.value) {
                TonNetwork.TESTNET
            } else {
                TonNetwork.MAINNET
            }
        }
    }
}