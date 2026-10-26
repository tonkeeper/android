package com.tonapps.perps.data

import com.tonapps.chainkit.lighter.trading.PerpsExecutionErrorKind
import com.tonapps.log.L
import io.JsonAny
import io.Serializer
import io.infrastructure.ClientError
import io.infrastructure.ClientException
import io.infrastructure.ServerError
import io.infrastructure.ServerException
import io.perpsapi.models.ErrorResponse
import java.io.IOException

sealed interface PerpsError {
    data object ServiceUnavailable : PerpsError
    data object NoAccount : PerpsError
    data object Unauthorized : PerpsError
    data object Forbidden : PerpsError
    data object Network : PerpsError
    data object BadRequest : PerpsError
    data object RoTokenUnavailable : PerpsError
    data object Conflict : PerpsError
    data object Unknown : PerpsError
}

internal fun Throwable.toPerpsError(): PerpsError {
    if (this is ClientException && statusCode == HTTP_BAD_REQUEST) {
        return badRequestError()
    }
    val statusCode = when (this) {
        is ClientException -> statusCode
        is ServerException -> statusCode
        is IOException -> return PerpsError.Network
        else -> return PerpsError.Unknown
    }

    return when (statusCode) {
        HTTP_BAD_REQUEST -> PerpsError.BadRequest
        HTTP_UNAUTHORIZED -> PerpsError.Unauthorized
        HTTP_FORBIDDEN -> PerpsError.Forbidden
        HTTP_CONFLICT -> PerpsError.Conflict
        HTTP_UNAVAILABLE -> PerpsError.ServiceUnavailable
        else -> PerpsError.Unknown
    }
}

fun PerpsError.toExecutionErrorKind(): PerpsExecutionErrorKind {
    return when (this) {
        PerpsError.Network -> PerpsExecutionErrorKind.Offline
        PerpsError.ServiceUnavailable -> PerpsExecutionErrorKind.ServerUnavailable
        PerpsError.BadRequest,
        PerpsError.RoTokenUnavailable,
        PerpsError.Conflict -> PerpsExecutionErrorKind.ServerRejected

        PerpsError.Unauthorized -> PerpsExecutionErrorKind.AuthExpired
        PerpsError.Forbidden -> PerpsExecutionErrorKind.CredentialsRevoked

        PerpsError.NoAccount,
        PerpsError.Unknown -> PerpsExecutionErrorKind.Unknown
    }
}

private fun ClientException.badRequestError(): PerpsError {
    val body = (response as? ClientError<*>)?.body?.toString()
    val code = body?.let { runCatching { Serializer.JSON.decodeFromString<ErrorResponse>(it).code }.getOrNull() }

    return if (code == CODE_RO_TOKEN_UNAVAILABLE) {
        PerpsError.RoTokenUnavailable
    } else {
        PerpsError.BadRequest
    }
}

internal fun Throwable.isRetryable(): Boolean {
    val (statusCode, body) = when (this) {
        is ClientException -> statusCode to (response as? ClientError<*>)?.body
        is ServerException -> statusCode to (response as? ServerError<*>)?.body
        else -> return false
    }
    val retryable = body?.toString()
        ?.let { runCatching { Serializer.JSON.decodeFromString<ErrorResponse>(it).retryable }.getOrNull() }

    return retryable ?: (statusCode == HTTP_UNAVAILABLE)
}

sealed interface PerpsSendError {
    data class Refused(val reason: PerpsSendReason, val code: String) : PerpsSendError
    data object Unknown : PerpsSendError
    data class Failed(val error: PerpsError) : PerpsSendError
}

internal fun Throwable.toPerpsSendError(): PerpsSendError {
    if (this !is ClientException) {
        return PerpsSendError.Unknown
    }
    if (statusCode == HTTP_UNAUTHORIZED || statusCode == HTTP_FORBIDDEN) {
        return PerpsSendError.Failed(toPerpsError())
    }
    if (statusCode != HTTP_BAD_REQUEST && statusCode != HTTP_CONFLICT) {
        return PerpsSendError.Unknown
    }
    val body = (response as? ClientError<*>)?.body?.toString() ?: return PerpsSendError.Unknown
    val error = try {
        Serializer.JSON.decodeFromString<ErrorResponse>(body)
    } catch (e: Throwable) {
        L.e(e)
        return PerpsSendError.Unknown
    }
    val reasonName = (error.details?.get("reason") as? JsonAny.Primitive)?.value as? String
    if (statusCode == HTTP_CONFLICT && reasonName != NO_RO_TOKEN_REASON) {
        return PerpsSendError.Unknown
    }
    val reason = when (reasonName) {
        "resign_required" -> PerpsSendReason.RESIGN_REQUIRED
        NO_RO_TOKEN_REASON -> PerpsSendReason.NO_RO_TOKEN
        "order_gone" -> PerpsSendReason.ORDER_GONE
        "batch_not_accepted" -> PerpsSendReason.BATCH_NOT_ACCEPTED
        "invalid_transaction" -> PerpsSendReason.INVALID_TRANSACTION
        "lighter_rejected" -> PerpsSendReason.LIGHTER_REJECTED
        "integrator_fee_mismatch" -> PerpsSendReason.INTEGRATOR_FEE_MISMATCH
        "integrator_not_approved" -> PerpsSendReason.INTEGRATOR_NOT_APPROVED
        else -> PerpsSendReason.OTHER
    }
    return PerpsSendError.Refused(reason, error.code)
}

class PerpsLoadException(val error: PerpsError) : Exception()

private const val CODE_RO_TOKEN_UNAVAILABLE = "readonly_token_unavailable"
private const val NO_RO_TOKEN_REASON = "no_ro_token"
private const val HTTP_BAD_REQUEST = 400
private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_FORBIDDEN = 403
private const val HTTP_CONFLICT = 409
private const val HTTP_UNAVAILABLE = 503
