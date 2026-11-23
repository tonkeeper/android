package com.tonapps.deposit.multicoin.moonpay.api

import kotlinx.serialization.Serializable
import java.io.IOException
import java.math.BigDecimal

@Serializable
data class MoonPayErrorDetail(
    val code: String? = null,
    val field: String? = null,
    val message: String? = null,
    val details: Details? = null,
) {

    @Serializable
    data class Details(
        @Serializable(with = MoonPayLenientString::class)
        val minBuyAmount: String? = null,
        @Serializable(with = MoonPayLenientString::class)
        val maxBuyAmount: String? = null,
    )
}

// Error body of the Platform API.
@Serializable
data class MoonPayServerError(
    val code: String? = null,
    val message: String? = null,
    val errors: List<MoonPayErrorDetail>? = null,
)

// The bound a quote request's `source.amount` fell outside of, in the quote's fiat currency.
sealed interface MoonPayBuyAmountLimit {
    data class BelowMinimum(val amount: BigDecimal) : MoonPayBuyAmountLimit
    data class AboveMaximum(val amount: BigDecimal) : MoonPayBuyAmountLimit
}

sealed class MoonPayApiException(message: String, cause: Throwable? = null) : Exception(message, cause) {

    // The request or reading its response failed: no network, timeout, TLS, a dropped connection.
    class Transport(cause: IOException) : MoonPayApiException("MoonPay API request failed", cause)

    class Http(val status: Int, val body: MoonPayServerError?) :
        MoonPayApiException("MoonPay API HTTP $status${describe(body)}") {

        // Set when a quote was rejected because the amount is under the minimum or over the maximum.
        val buyAmountLimit: MoonPayBuyAmountLimit?
            get() {
                for (detail in body?.errors.orEmpty()) {
                    if (detail.field != SOURCE_AMOUNT_FIELD) {
                        continue
                    }
                    detail.details?.minBuyAmount?.toBigDecimalOrNull()?.let {
                        return MoonPayBuyAmountLimit.BelowMinimum(it)
                    }
                    detail.details?.maxBuyAmount?.toBigDecimalOrNull()?.let {
                        return MoonPayBuyAmountLimit.AboveMaximum(it)
                    }
                }
                return null
            }
    }

    class RateLimited(val body: MoonPayServerError?) :
        MoonPayApiException("MoonPay API rate limit exceeded${describe(body)}")

    // Carries no parser exception: its message quotes the response, which may hold card and wallet details.
    class Decoding(response: String?) : MoonPayApiException("MoonPay API response $response could not be decoded")

    private companion object {
        const val SOURCE_AMOUNT_FIELD = "source.amount"

        fun describe(body: MoonPayServerError?): String {
            body ?: return ""
            val errors = body.errors.orEmpty().joinToString("; ") { error ->
                listOfNotNull(error.field, error.message).joinToString(": ")
            }
            val parts = listOfNotNull(body.code, body.message, errors.ifEmpty { null })
            return if (parts.isEmpty()) {
                ""
            } else {
                " (${parts.joinToString(" — ")})"
            }
        }
    }
}
