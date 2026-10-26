package com.tonapps.bus.core.aptabase

import com.tonapps.log.L
import java.io.IOException

private const val EVENTS_PATH = "/api/v0/events"

class AptabaseDispatcher(
    baseUrl: String,
    appKey: String,
    private val httpClient: AptabaseHttpClient,
) {

    enum class Outcome {
        Delivered,
        Rejected,
        Retry,
    }

    private val url = baseUrl.trimEnd('/') + EVENTS_PATH

    private val headers = mapOf(
        "Content-Type" to "application/json",
        "App-Key" to appKey,
    )

    fun send(events: List<AptabaseEvent>): Outcome {
        if (events.isEmpty()) {
            return Outcome.Delivered
        }
        require(events.size <= MAX_BATCH_SIZE) { "Aptabase batch is limited to $MAX_BATCH_SIZE events" }
        val body = AptabaseCoding.encodeWire(events)
        val response = try {
            httpClient.post(url, headers, body)
        } catch (e: IOException) {
            L.w("Aptabase: retrying ${events.size} events after ${e.javaClass.simpleName}: ${e.message}")
            return Outcome.Retry
        }
        return when {
            response.code in SUCCESS_STATUS_CODES -> {
                L.d("Aptabase: delivered ${events.size} events, status ${response.code}")
                Outcome.Delivered
            }

            response.code >= SERVER_ERROR_STATUS_CODE || response.code in RETRIABLE_STATUS_CODES -> {
                L.w("Aptabase: retrying ${events.size} events after status ${response.code}")
                Outcome.Retry
            }

            else -> {
                L.w("Aptabase: dropping ${events.size} events, status ${response.code} ${response.body}")
                Outcome.Rejected
            }
        }
    }

    companion object {
        const val MAX_BATCH_SIZE = 25

        private val SUCCESS_STATUS_CODES = 200..299
        private const val SERVER_ERROR_STATUS_CODE = 500
        private val RETRIABLE_STATUS_CODES = setOf(408, 425, 429)
    }
}
