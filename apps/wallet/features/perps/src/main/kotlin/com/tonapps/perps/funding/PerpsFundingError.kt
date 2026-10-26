package com.tonapps.perps.funding

import com.tonapps.log.L
import com.tonapps.perps.data.PerpsError
import com.tonapps.perps.data.PerpsLoadException
import com.tonapps.perps.data.toPerpsError
import io.JsonAny
import io.Serializer
import io.infrastructure.ClientError
import io.infrastructure.ClientException
import io.infrastructure.ServerError
import io.infrastructure.ServerException
import io.perpsapi.models.ErrorResponse
import java.io.IOException
import java.math.BigDecimal
import kotlinx.serialization.SerializationException

sealed interface PerpsFundingError {

    val retryable: Boolean

    data class Refused(
        val code: String,
        val reason: String?,
        val statusCode: Int,
        override val retryable: Boolean,
        val minUsdAmount: BigDecimal? = null,
    ) : PerpsFundingError {

        val needsAuthorization: Boolean
            get() = code in AUTHORIZATION_CODES
    }

    data class Transport(val error: PerpsError) : PerpsFundingError {
        override val retryable: Boolean
            get() = error == PerpsError.Network || error == PerpsError.ServiceUnavailable
    }

    data object Unknown : PerpsFundingError {
        override val retryable: Boolean
            get() = false
    }
}

/** Why a deposit could not be quoted, as the amount screen tells it. */
sealed interface PerpsDepositQuoteError {
    data class BelowMinimum(val minUsd: BigDecimal?) : PerpsDepositQuoteError
    data object InsufficientBalance : PerpsDepositQuoteError
    data class InsufficientNetworkFee(val symbol: String) : PerpsDepositQuoteError
    data object ServiceUnavailable : PerpsDepositQuoteError
    data object QuoteFailed : PerpsDepositQuoteError
    data object Failed : PerpsDepositQuoteError
}

fun Throwable.toDepositQuoteError(): PerpsDepositQuoteError {
    return when (this) {
        is PerpsDepositException.InsufficientBalance -> PerpsDepositQuoteError.InsufficientBalance
        is PerpsDepositException.InsufficientNetworkFee -> PerpsDepositQuoteError.InsufficientNetworkFee(symbol)
        is PerpsDepositException.QuoteRejected -> PerpsDepositQuoteError.QuoteFailed
        // A quote the client can't read is no quote at all.
        is SerializationException -> PerpsDepositQuoteError.QuoteFailed
        is PerpsDepositException -> PerpsDepositQuoteError.Failed
        is PerpsLoadException -> if (error == PerpsError.Network || error == PerpsError.ServiceUnavailable) {
            PerpsDepositQuoteError.ServiceUnavailable
        } else {
            PerpsDepositQuoteError.QuoteFailed
        }

        else -> when (val error = toPerpsFundingError()) {
            is PerpsFundingError.Transport -> PerpsDepositQuoteError.ServiceUnavailable
            is PerpsFundingError.Refused -> when {
                error.retryable -> PerpsDepositQuoteError.ServiceUnavailable
                error.reason == REASON_AMOUNT_TOO_LOW -> PerpsDepositQuoteError.BelowMinimum(error.minUsdAmount)
                error.reason == REASON_INSUFFICIENT_BALANCE -> PerpsDepositQuoteError.InsufficientBalance
                else -> PerpsDepositQuoteError.QuoteFailed
            }

            PerpsFundingError.Unknown -> PerpsDepositQuoteError.Failed
        }
    }
}

fun Throwable.toPerpsFundingError(): PerpsFundingError {
    return when (this) {
        is IOException -> PerpsFundingError.Transport(PerpsError.Network)
        is ClientException -> envelope(statusCode, (response as? ClientError<*>)?.body)
            ?: PerpsFundingError.Transport(toPerpsError())
        is ServerException -> envelope(statusCode, (response as? ServerError<*>)?.body)
            ?: PerpsFundingError.Transport(toPerpsError())
        else -> PerpsFundingError.Unknown
    }
}

private fun envelope(statusCode: Int, body: Any?): PerpsFundingError? {
    val raw = body?.toString()?.takeIf { it.isNotBlank() } ?: return null
    val error = try {
        Serializer.JSON.decodeFromString<ErrorResponse>(raw)
    } catch (e: Throwable) {
        L.e(e)
        return null
    }
    return PerpsFundingError.Refused(
        code = error.code,
        reason = error.detail(DETAIL_REASON),
        statusCode = statusCode,
        retryable = error.retryable,
        minUsdAmount = error.detail(DETAIL_MIN_USD_AMOUNT)?.toBigDecimalOrNull()?.takeIf { it.signum() > 0 },
    )
}

private fun ErrorResponse.detail(key: String): String? = (details?.get(key) as? JsonAny.Primitive)?.value as? String

internal const val REASON_AMOUNT_TOO_LOW = "amount_too_low"
private const val REASON_INSUFFICIENT_BALANCE = "insufficient_balance"
private const val DETAIL_REASON = "reason"
private const val DETAIL_MIN_USD_AMOUNT = "min_usd_amount"

private val AUTHORIZATION_CODES = setOf(
    "operation_proof_required",
    "operation_proof_invalid",
    "address_proof_required",
)
