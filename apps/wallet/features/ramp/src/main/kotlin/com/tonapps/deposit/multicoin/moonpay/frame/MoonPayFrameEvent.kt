package com.tonapps.deposit.multicoin.moonpay.frame

import com.tonapps.deposit.multicoin.moonpay.api.MoonPayErrorDetail
import com.tonapps.deposit.multicoin.moonpay.api.MoonPaySavedCard
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

sealed interface MoonPayFrameEvent {

    data object Handshake : MoonPayFrameEvent

    data object Ready : MoonPayFrameEvent

    data class Complete(val completion: MoonPayFrameCompletion) : MoonPayFrameEvent

    data class TransactionCreated(val transaction: MoonPayTransaction) : MoonPayFrameEvent

    data class Challenge(val url: String) : MoonPayFrameEvent

    data class Cancelled(val payload: JsonElement?) : MoonPayFrameEvent {

        // A cancelled buy challenge names the transaction it leaves behind.
        val transactionId: String?
            get() = payload.string("transactionId")
    }

    data object ButtonPressed : MoonPayFrameEvent

    // A challenge opened with `resume` settled what a wallet payment needed: the customer pays on the buy button again.
    data object Resolved : MoonPayFrameEvent

    data class Error(val error: MoonPayFrameError) : MoonPayFrameEvent

    data class Unknown(val kind: String, val payload: JsonElement?) : MoonPayFrameEvent

    companion object {

        fun from(message: MoonPayFrameMessage): MoonPayFrameEvent {
            val payload = message.payload
            return when (message.kind) {
                "handshake" -> Handshake
                "ready" -> Ready
                "complete" -> Complete(MoonPayFrameCompletion.from(payload))
                "transactionCreated" -> {
                    val transaction = payload.field("transaction")
                        ?: throw MoonPayFrameException.Malformed("transactionCreated without transaction")
                    TransactionCreated(transaction.decode(MoonPayTransaction.serializer()))
                }
                "challenge" -> {
                    val url = payload.string("url") ?: throw MoonPayFrameException.Malformed("challenge without url")
                    Challenge(url)
                }
                "cancelled" -> Cancelled(payload)
                "buttonPressed" -> ButtonPressed
                "resolved" -> Resolved
                "error" -> Error(MoonPayFrameError.from(payload))
                else -> Unknown(message.kind, payload)
            }
        }
    }
}

sealed interface MoonPayFrameCompletion {

    data class Connection(val result: MoonPayConnectionResult) : MoonPayFrameCompletion

    // `sessionUpgraded`: the customer signed in during the purchase, so the session must be created again.
    data class Transaction(
        val transaction: MoonPayTransaction,
        val sessionUpgraded: Boolean = false,
    ) : MoonPayFrameCompletion

    data class Card(val card: MoonPaySavedCard) : MoonPayFrameCompletion

    data class Identity(val id: String) : MoonPayFrameCompletion

    data class Status(val status: String) : MoonPayFrameCompletion

    data class Raw(val payload: JsonElement?) : MoonPayFrameCompletion

    companion object {

        fun from(payload: JsonElement?): MoonPayFrameCompletion {
            if (payload !is JsonObject) {
                return Raw(payload)
            }
            val flow = payload.string("flow")
            val transaction = payload.field("transaction")
            val card = payload.field("card")
            val status = payload.string("status")
            return when {
                flow == "buy" -> Transaction(
                    transaction?.decode(MoonPayTransaction.serializer())
                        ?: throw MoonPayFrameException.Malformed("buy challenge completed without transaction"),
                    sessionUpgraded = payload.boolean("sessionUpgraded"),
                )
                flow == "identity" && status == null -> Identity(
                    payload.string("identityId")
                        ?: throw MoonPayFrameException.Malformed("identity challenge completed without identityId"),
                )
                flow != null -> status?.let(::Status) ?: Raw(payload)
                transaction != null -> Transaction(
                    transaction.decode(MoonPayTransaction.serializer()),
                    sessionUpgraded = payload.boolean("sessionUpgraded"),
                )
                card != null -> Card(card.decode(MoonPaySavedCard.serializer()))
                status == null -> Raw(payload)
                MoonPayConnectionStatus.of(status) != null -> Connection(
                    payload.decode(MoonPayConnectionResult.serializer()),
                )
                else -> Status(status)
            }
        }
    }
}

data class MoonPayFrameError(
    val code: String,
    val message: String,
    val errors: List<MoonPayErrorDetail>? = null,
) {

    companion object {

        fun from(payload: JsonElement?): MoonPayFrameError {
            return MoonPayFrameError(
                code = payload.string("code") ?: "unknown",
                message = payload.string("message") ?: "Unknown error",
                errors = payload.field("errors")?.let { errors ->
                    runCatching { errors.decode(ListSerializer(MoonPayErrorDetail.serializer())) }.getOrNull()
                },
            )
        }
    }
}

private fun JsonElement?.field(key: String): JsonElement? = (this as? JsonObject)?.get(key)

private fun JsonElement?.string(key: String): String? {
    return (field(key) as? JsonPrimitive)?.takeIf { it.isString }?.content
}

private fun JsonElement?.boolean(key: String): Boolean {
    return (field(key) as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull == true
}

private fun <T> JsonElement.decode(serializer: KSerializer<T>): T {
    return try {
        moonPayFrameJson.decodeFromJsonElement(serializer, this)
    } catch (e: SerializationException) {
        throw MoonPayFrameException.Malformed("unexpected ${serializer.descriptor.serialName}", e)
    } catch (e: IllegalArgumentException) {
        throw MoonPayFrameException.Malformed("unexpected ${serializer.descriptor.serialName}", e)
    }
}
