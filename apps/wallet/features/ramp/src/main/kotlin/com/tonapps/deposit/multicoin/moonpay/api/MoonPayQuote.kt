package com.tonapps.deposit.multicoin.moonpay.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration

@Serializable
data class MoonPayAsset(
    // CeFi asset code, e.g. `usd`, `ton`.
    val code: String? = null,
    // CAIP-19 identifier of a DeFi token.
    val caip19: String? = null,
)

@Serializable
data class MoonPayMoney(
    @Serializable(with = MoonPayLenientString::class)
    val amount: String? = null,
    val asset: MoonPayAsset,
)

@Serializable
data class MoonPayWalletReference(
    val address: String,
    val tag: String? = null,
)

@Serializable
data class MoonPayPaymentMethodReference(
    val type: String? = null,
    val id: String? = null,
)

@Serializable
enum class MoonPayFeeBehavior {
    @SerialName("inclusive")
    Inclusive,

    @SerialName("exclusive")
    Exclusive,
}

// Exactly one of `source.amount` / `destination.amount` is set.
@Serializable
data class MoonPayBuyQuoteRequest(
    val source: MoonPayMoney,
    val destination: MoonPayMoney,
    val wallet: MoonPayWalletReference? = null,
    val paymentMethod: MoonPayPaymentMethodReference? = null,
    val feeBehavior: MoonPayFeeBehavior? = null,
)

@Serializable
data class MoonPayBuyQuote(
    val source: MoonPayMoney,
    val destination: MoonPayMoney,
    // Keyed by fee kind: `network`, `moonpay`, `ecosystem`, `defi`, `partner`.
    val fees: Map<String, MoonPayMoney>? = null,
    val wallet: MoonPayWalletReference? = null,
    val paymentMethod: MoonPayPaymentMethodReference? = null,
    val feeBehavior: MoonPayFeeBehavior? = null,
    // Server time, ISO 8601.
    val expiresAt: String? = null,
    val executable: Boolean = false,
    @Serializable(with = MoonPayLenientString::class)
    val exchangeRate: String? = null,
    val exchangeRateType: String? = null,
    val signature: String,
    val challenge: JsonObject? = null,
    @Serializable(with = MoonPayPaymentDisclosuresSerializer::class)
    val paymentDisclosures: List<PaymentDisclosure>? = null,
    // `expiresAt` on this device's elapsed-realtime clock, which keeps running in sleep;
    // null when the response carried no server time.
    @Transient
    val expiresAtMark: ComparableTimeMark? = null,
    // How far MoonPay's clock runs ahead of this device's, from the response this quote came in; not MoonPay's
    // field. Kept through serialization, unlike the mark: the payment screen gets the quote as JSON.
    val serverClockOffsetMs: Long? = null,
) {

    val challengeUrl: String?
        get() = (challenge?.get("url") as? JsonPrimitive)?.takeIf { it.isString }?.content

    val lifetime: Duration?
        get() = expiresAtMark?.let { -it.elapsedNow() }

    // A quote with an unknown expiry is taken as valid.
    fun isFresh(remaining: Duration): Boolean {
        return lifetime?.let { it >= remaining } ?: true
    }

    @Serializable
    data class PaymentDisclosure(
        val id: String,
        @Serializable(with = MoonPayLenientString::class)
        val version: String? = null,
        // Not MoonPay's: an element that could not be read, its raw JSON in [id].
        @Transient
        val undecodable: Boolean = false,
    )
}

@Serializable
internal class MoonPayBuyQuoteResponse(val data: MoonPayBuyQuote)
