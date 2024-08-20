package com.tonapps.wallet.api

import android.os.SystemClock
import io.tonapi.infrastructure.Serializer
import com.squareup.moshi.adapter
import io.tonapi.infrastructure.ClientException
import android.util.Log
import com.tonapps.network.OkHttpError
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
            if (e is ClientException && e.statusCode != 429 && 500 >= e.statusCode) {
                return null
            } else if (e is OkHttpError && e.statusCode != 429 && 500 >= e.statusCode) {
                return null
            }
            Log.e("TONKeeperLog", "error request", e)
            /*else if (e is InterruptedIOException || e is SocketException) {
                return null
            }*/
        }
        SystemClock.sleep(delay)
    }
    return null
}
