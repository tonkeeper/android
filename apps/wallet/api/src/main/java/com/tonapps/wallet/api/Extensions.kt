package com.tonapps.wallet.api

import android.os.SystemClock
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.tonapps.log.L
import com.tonapps.network.OkHttpError
import com.tonapps.network.backoff.ExponentialBackoff
import io.infrastructure.ClientError
import io.infrastructure.ClientException
import io.infrastructure.ServerError
import kotlinx.coroutines.CancellationException
import kotlinx.io.IOException
import okhttp3.Response
import java.net.SocketTimeoutException

fun <R> withRetry(
    times: Int = 5,
    backoff: ExponentialBackoff = ExponentialBackoff(),
    retryBlock: () -> R
): R? {
    var index = -1
    do {
        index++
        try {
            return retryBlock()
        } catch (e: CancellationException) {
            throw e
        } catch (e: SocketTimeoutException) {
            L.e("RetryLogNew", "SocketTimeoutException occurred: ${e.message}", e)
            return null
        } catch (e: IOException) {
            L.e("RetryLogNew", "IOException occurred: ${e.message}", e)
            return null
        } catch (e: Throwable) {
            L.e("RetryLogNew", "Error occurred: ${e.message}", e)
            val statusCode = e.getHttpStatusCode()

            if (statusCode == 429 || statusCode == 401 || statusCode == 502 || statusCode == 520) {
                SystemClock.sleep(backoff.getDelayMs(index))
                continue
            }

            if (statusCode >= 500 || statusCode == 404 || statusCode == 400) {
                return null
            }

            FirebaseCrashlytics.getInstance().recordException(e)
        }
    } while (index < times)

    return null
}

private fun Throwable.getHttpStatusCode(): Int {
    return when (this) {
        is ClientException -> statusCode
        is OkHttpError -> statusCode
        else -> 0
    }
}

fun Throwable.getDebugMessage(): String? {
    return when (this) {
        is ClientException -> getHttpBodyMessage()
        is OkHttpError -> body
        else -> message
    }
}

fun Response.readBody(): String {
    return use { body.string() }
}

private fun ClientException.getHttpBodyMessage(): String {
    return when (response) {
        is ClientError<*> -> (response as ClientError<*>).body.toString()
        is ServerError<*> -> (response as ServerError<*>).body.toString()
        else -> response.toString()
    }
}
