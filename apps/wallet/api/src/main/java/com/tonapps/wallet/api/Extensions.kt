package com.tonapps.wallet.api

import android.os.SystemClock
import io.tonapi.infrastructure.Serializer
import com.squareup.moshi.adapter
import io.tonapi.infrastructure.ClientException
import android.util.Log
import android.widget.Toast
import com.google.firebase.crashlytics.BuildConfig
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.tonapps.network.OkHttpError
import io.tonapi.infrastructure.ClientError
import io.tonapi.infrastructure.Response
import io.tonapi.infrastructure.ServerError
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

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
    delay: Long = 500,
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
            return null
        } catch (e: IOException) {
            return null
        } catch (e: Throwable) {
            val statusCode = e.getHttpStatusCode()
            if (statusCode == 429 || statusCode == 401 || statusCode == 502 || statusCode == 520) {
                SystemClock.sleep(delay)
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

private fun ClientException.getHttpBodyMessage(): String {
    return when (response) {
        is ClientError<*> -> (response as ClientError<*>).body.toString()
        is ServerError<*> -> (response as ServerError<*>).body.toString()
        else -> response.toString()
    }
}