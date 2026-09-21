package com.tonapps.perps.data

import io.infrastructure.ClientException
import io.infrastructure.ServerException
import java.io.IOException

sealed interface PerpsError {
    data object ServiceUnavailable : PerpsError
    data object NoAccount : PerpsError
    data object Unauthorized : PerpsError
    data object Network : PerpsError
    data object BadRequest : PerpsError
    data object Unknown : PerpsError
}

internal fun Throwable.toPerpsError(): PerpsError {
    val statusCode = when (this) {
        is ClientException -> statusCode
        is ServerException -> statusCode
        is IOException -> return PerpsError.Network
        else -> return PerpsError.Unknown
    }

    return when (statusCode) {
        HTTP_BAD_REQUEST -> PerpsError.BadRequest
        HTTP_UNAUTHORIZED -> PerpsError.Unauthorized
        HTTP_UNAVAILABLE -> PerpsError.ServiceUnavailable
        else -> PerpsError.Unknown
    }
}

class PerpsLoadException(val error: PerpsError) : Exception()

private const val HTTP_BAD_REQUEST = 400
private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_UNAVAILABLE = 503
