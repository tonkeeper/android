package com.tonapps.deposit.multicoin.moonpay

import com.tonapps.deposit.multicoin.moonpay.api.MoonPayApiException
import com.tonapps.deposit.multicoin.moonpay.api.MoonPayEnvironment
import com.tonapps.deposit.multicoin.moonpay.api.MoonPayPlatformApi
import com.tonapps.deposit.multicoin.moonpay.api.MoonPayTransactionDetails
import com.tonapps.deposit.multicoin.catchingResult
import com.tonapps.deposit.multicoin.moonpay.frame.MoonPayTheme

class MoonPayTransactionRepository(
    private val connections: MoonPayConnectionRepository,
    private val platformApi: (MoonPayEnvironment) -> MoonPayPlatformApi,
) {

    suspend fun transaction(id: String, theme: MoonPayTheme?): Result<MoonPayTransactionDetails> {
        return connections.withSilentConnection(theme) { connection ->
            platformApi(connection.environment).transaction(connection.credentials.accessToken, id)
        }
    }

    // `sinceMs` is on MoonPay's clock.
    suspend fun transactionsSince(sinceMs: Long, theme: MoonPayTheme?): Result<List<MoonPayTransactionDetails>> {
        return connections.withSilentConnection(theme) { connection ->
            platformApi(connection.environment).transactions(connection.credentials.accessToken, sinceMs)
        }
    }
}

// Expired or refused credentials connect again, but never ask the customer to sign in. A token refused right
// after a fresh connect is final.
suspend fun <T> MoonPayConnectionRepository.withSilentConnection(
    theme: MoonPayTheme?,
    block: suspend (MoonPayConnection) -> Result<T>,
): Result<T> = catchingResult {
    val connection = ensureConnected(theme, interactive = false).getOrThrow()
    val result = block(connection)
    if ((result.exceptionOrNull() as? MoonPayApiException.Http)?.status != HTTP_UNAUTHORIZED) {
        return@catchingResult result.getOrThrow()
    }
    invalidate(connection)
    block(ensureConnected(theme, interactive = false).getOrThrow()).getOrThrow()
}

private const val HTTP_UNAUTHORIZED = 401
