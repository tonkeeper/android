package com.tonapps.wallet.data.multichain.realtime

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

@Serializable
internal data class RealtimeEnvelope(
    @SerialName("v") val version: Int,
    @SerialName("event") val event: String,
    @SerialName("wallet_id") val walletId: String,
    @SerialName("seq") val seq: Long,
)

internal object RealtimeEnvelopeParser {

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(payload: ByteArray): Result<RealtimeEnvelope> {
        return try {
            Result.success(json.decodeFromString<RealtimeEnvelope>(payload.decodeToString()))
        } catch (e: SerializationException) {
            Result.failure(e)
        } catch (e: IllegalArgumentException) {
            Result.failure(e)
        }
    }
}
