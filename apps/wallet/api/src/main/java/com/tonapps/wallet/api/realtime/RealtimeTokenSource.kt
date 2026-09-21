package com.tonapps.wallet.api.realtime

import com.tonapps.log.L
import com.tonapps.wallet.api.core.MultichainAPI
import io.infrastructure.ApiResponse
import io.infrastructure.ClientError
import io.infrastructure.ServerError
import io.infrastructure.Success
import io.walletapi.models.RealtimeToken
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection

private const val REALTIME_DISABLED_ERROR = "realtime_disabled"

sealed interface RealtimeTokenResult {
    data class Token(val value: String) : RealtimeTokenResult
    data object Forbidden : RealtimeTokenResult
    data object DisabledByBackend : RealtimeTokenResult
    data class Failure(val error: Throwable) : RealtimeTokenResult
}

class RealtimeTokenSource(
    private val multichain: MultichainAPI,
) {

    suspend fun connectionToken(): RealtimeTokenResult = request {
        multichain.wallets.getRealtimeConnectionTokenWithHttpInfo()
    }

    suspend fun subscriptionToken(walletId: String): RealtimeTokenResult = request {
        multichain.wallets.getWalletRealtimeTokenWithHttpInfo(walletId = walletId, xWalletId = walletId)
    }

    private suspend fun request(
        call: () -> ApiResponse<RealtimeToken?>,
    ): RealtimeTokenResult = withContext(Dispatchers.IO) {
        val response = try {
            call()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return@withContext RealtimeTokenResult.Failure(e)
        }
        response.toResult()
    }

    private fun ApiResponse<RealtimeToken?>.toResult(): RealtimeTokenResult = when (this) {
        is Success -> data?.token?.let(RealtimeTokenResult::Token)
            ?: RealtimeTokenResult.Failure(IllegalStateException("Realtime token response has no token"))
        is ClientError -> when (statusCode) {
            HttpURLConnection.HTTP_FORBIDDEN -> RealtimeTokenResult.Forbidden
            else -> RealtimeTokenResult.Failure(IllegalStateException("Realtime token request failed: $statusCode"))
        }
        is ServerError -> if (body?.toString()?.contains(REALTIME_DISABLED_ERROR) == true) {
            L.w("Realtime is disabled on the backend")
            RealtimeTokenResult.DisabledByBackend
        } else {
            RealtimeTokenResult.Failure(IllegalStateException("Realtime token request failed: $statusCode"))
        }
        else -> RealtimeTokenResult.Failure(IllegalStateException("Unexpected realtime token response: $statusCode"))
    }
}
