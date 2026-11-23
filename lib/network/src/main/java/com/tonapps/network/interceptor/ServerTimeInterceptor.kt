package com.tonapps.network.interceptor

import android.os.SystemClock
import com.tonapps.time.ServerTimeProvider
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Passes the `Date` header of responses from [domains] and their subdomains to
 * [ServerTimeProvider]. Add it with `addNetworkInterceptor` so it times the exchange itself and
 * never sees a response from the local cache.
 */
class ServerTimeInterceptor(
    private val provider: ServerTimeProvider,
    private val domains: Collection<String>,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        // Another server's date would become the app's time.
        if (!isTrusted(request.url.host)) {
            return chain.proceed(request)
        }

        val sentAt = SystemClock.elapsedRealtime()
        val response = chain.proceed(request)
        val receivedAt = SystemClock.elapsedRealtime()

        // A shared cache answering for the server keeps the date of the original response.
        val age = response.header(AGE_HEADER)?.toLongOrNull() ?: 0
        val date = response.headers.getDate(DATE_HEADER)
        if (date != null && age == 0L) {
            provider.onServerDate(date.time, sentAt, receivedAt)
        }
        return response
    }

    private fun isTrusted(host: String): Boolean {
        return domains.any { host == it || host.endsWith(".$it") }
    }

    private companion object {
        const val DATE_HEADER = "Date"
        const val AGE_HEADER = "Age"
    }
}
