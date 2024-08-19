package com.tonapps.wallet.api

import io.tonapi.infrastructure.Serializer
import com.squareup.moshi.adapter
import io.tonapi.infrastructure.ClientException
import android.util.Log
import com.tonapps.network.OkHttpError
import kotlinx.coroutines.delay
import java.io.InterruptedIOException
import java.net.SocketException

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

suspend fun <R> withRetry(
    times: Int = 5,
    delay: Long = 300,
    block: () -> R
): R? {
    for (i in 0 until times) {
        try {
            return block()
        } catch (e: Throwable) {
            Log.e("TONKeeperLog", "error request", e)
            if (e is ClientException && e.statusCode != 429 && 500 > e.statusCode) {
                return null
            } else if (e is OkHttpError && e.statusCode != 429 && 500 > e.statusCode) {
                return null
            }/* else if (e is InterruptedIOException || e is SocketException) {
                return null
            }*/
        }
        delay(delay)
    }
    return null
}
