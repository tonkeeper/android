package com.tonapps.network.interceptor

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Retries TronGrid (and similar) requests when the server returns HTTP 429.
 *
 * Wait duration is taken from:
 * 1. `Retry-After` header (seconds), or
 * 2. body text like `suspended for 1 s`, or
 * 3. [defaultWaitMs] as a fallback.
 */
class Tron429RetryInterceptor(
    private val maxRetries: Int = 5,
    private val defaultWaitMs: Long = 1_000L,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        var attempt = 0
        while (true) {
            val response = chain.proceed(chain.request())
            if (response.code != HTTP_TOO_MANY_REQUESTS || attempt >= maxRetries) {
                return response
            }

            val waitMs = parseWaitMs(response).coerceAtLeast(defaultWaitMs)
            response.close()
            attempt++
            Thread.sleep(waitMs)
        }
    }

    private fun parseWaitMs(response: Response): Long {
        response.header(HEADER_RETRY_AFTER)
            ?.trim()
            ?.toLongOrNull()
            ?.takeIf { it >= 0L }
            ?.let { return it * 1_000L }

        val body = runCatching {
            response.peekBody(MAX_PEEK_BYTES).string()
        }.getOrNull().orEmpty()

        SUSPENDED_FOR_SECONDS.find(body)
            ?.groupValues
            ?.getOrNull(1)
            ?.toLongOrNull()
            ?.takeIf { it >= 0L }
            ?.let { return it * 1_000L }

        return defaultWaitMs
    }

    private companion object {
        const val HTTP_TOO_MANY_REQUESTS = 429
        const val HEADER_RETRY_AFTER = "Retry-After"
        const val MAX_PEEK_BYTES = 8_192L

        val SUSPENDED_FOR_SECONDS = Regex(
            pattern = """suspended for\s+(\d+)\s*s""",
            option = RegexOption.IGNORE_CASE,
        )
    }
}
