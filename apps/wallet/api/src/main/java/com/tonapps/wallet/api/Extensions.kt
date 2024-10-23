package com.tonapps.wallet.api

import android.os.SystemClock
import io.tonapi.infrastructure.Serializer
import com.squareup.moshi.adapter
import io.tonapi.infrastructure.ClientException
import android.util.Log
import com.tonapps.network.OkHttpError
import io.tonapi.infrastructure.ClientError
import io.tonapi.infrastructure.Response
import io.tonapi.infrastructure.ServerError
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException

@OptIn(ExperimentalStdlibApi::class)
inline fun <reified T> toJSON(obj: T?): String {
    if (obj == null) {
        return ""
    }
    return Serializer.moshi.adapter<T>().toJson(obj)
}

@OptIn(ExperimentalStdlibApi::class)
inline fun <reified T> fromJSON(json: String): T {
    return Serializer.moshi.adapter<T>().fromJson(json)!!
}

fun <R> withRetry(
    times: Int = 5,
    delay: Long = 300,
    retryBlock: () -> R
): R? {
    repeat(times) {
        try {
            return retryBlock()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            val statusCode = e.getHttpStatusCode()
            val message = e.getDebugMessage()
            Log.e("TonkeeperLog", "Error in retry block(code=$statusCode): $message", e)
            if (statusCode == 429) { // Too many requests
                SystemClock.sleep((3000..5000).random().toLong())
                return withRetry(times, delay, retryBlock)
            } else if (statusCode >= 500 || statusCode == 404) {
                return null
            }
        }
        SystemClock.sleep(delay)
    }
    return null
}

private fun Throwable.getHttpStatusCode(): Int {
    return when (this) {
        is ClientException -> statusCode
        is OkHttpError -> statusCode
        else -> 0
    }
}

private fun Throwable.getDebugMessage(): String {
    return when (this) {
        is ClientException -> getHttpBodyMessage()
        is OkHttpError -> body
        else -> message ?: "Unknown error"
    }
}

private fun ClientException.getHttpBodyMessage(): String {
    return when (response) {
        is ClientError<*> -> (response as ClientError<*>).body.toString()
        is ServerError<*> -> (response as ServerError<*>).body.toString()
        else -> response.toString()
    }
}