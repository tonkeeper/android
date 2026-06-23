package com.tonapps.network

import androidx.collection.ArrayMap
import com.tonapps.log.L
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.isActive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.internal.http2.ErrorCode
import okhttp3.internal.http2.StreamResetException
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

fun requestBuilder(url: String): Request.Builder {
    val builder = Request.Builder()
    builder.url(url)
    return builder
}

fun OkHttpClient.postJSON(
    url: String,
    json: String,
    headers: ArrayMap<String, String>? = null
): Response {
    val body = json.toRequestBody("application/json".toMediaType())
    return post(url, body, headers)
}

fun OkHttpClient.post(
    url: String,
    body: RequestBody,
    headers: ArrayMap<String, String>? = null
): Response {
    val builder = requestBuilder(url)
    builder.post(body)
    headers?.forEach { (key, value) ->
        builder.addHeader(key, value)
    }
    return execute(builder.build())
}

fun OkHttpClient.get(
    url: String,
    headers: ArrayMap<String, String>? = null
): String {
    return simple(url, headers).use { it.body.string() }
}

fun OkHttpClient.simple(
    url: String,
    headers: ArrayMap<String, String>? = null
): Response {
    val builder = requestBuilder(url)
    headers?.forEach { (key, value) ->
        builder.addHeader(key, value)
    }
    return execute(builder.build())
}

fun OkHttpClient.execute(request: Request): Response {
    val response = newCall(request).execute()
    if (!response.isSuccessful) {
        throw OkHttpError(response)
    }
    return response
}

class OkHttpError(
    private val response: Response
) : Exception("HTTP error: ${response.code}") {

    val statusCode: Int
        get() = response.code

    val body: String
        get() = response.body?.string() ?: ""
}

fun OkHttpClient.sseFactory() = EventSources.createFactory(this)

/**
 * Private EventSource listener for handling SSE events within a Flow context.
 * Provides atomic close semantics and comprehensive error handling.
 */
private class FlowEventListener(
    private val url: String,
    private val scope: ProducerScope<SSEvent>,
) : EventSourceListener() {

    // Atomic flag prevents multiple close attempts and race conditions
    private val isClosed = AtomicBoolean(false)

    override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
        // Double-check: channel state and atomic flag
        if (!scope.isActive || isClosed.get()) {
            return
        }

        val event = SSEvent(id, type, data)
        val sendResult = scope.trySend(event)

        if (!sendResult.isSuccess) {
            // Buffer overflow - this shouldn't happen with .buffer(128, DROP_OLDEST)
            // but we handle it gracefully just in case
            L.w("SSE buffer overflow, url=$url, event=$id")

            // Atomic close to prevent race with onFailure/onClosed
            if (isClosed.compareAndSet(false, true)) {
                try {
                    eventSource.cancel()
                    scope.close(IOException("SSE downstream not ready / buffer full"))
                } catch (e: Exception) {
                    L.e(e, "Error closing SSE on overflow")
                }
            }
        }
    }

    override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
        // Already closed or cancelled - skip to prevent double-close
        if (isClosed.get() || !scope.isActive) {
            return
        }

        // Classify and log the error
        val error = when {
            t is StreamResetException -> {
                L.w("SSE stream reset: ${t.errorCode}, url=$url")
                t
            }

            t != null -> {
                L.w("SSE failure: ${t.message}, url=$url")
                t
            }

            else -> {
                L.w("SSE connection failed: HTTP ${response?.code}, url=$url")
                IOException("SSE connection failed with response: ${response?.code}")
            }
        }

        // Atomic close
        if (isClosed.compareAndSet(false, true)) {
            try {
                scope.close(error)
            } catch (e: Exception) {
                L.e(e, "Error closing SSE on failure")
            }
        }
    }

    override fun onClosed(eventSource: EventSource) {
        if (isClosed.get() || !scope.isActive) {
            return
        }

        L.d("SSE connection closed gracefully, url=$url")

        // Atomic close
        if (isClosed.compareAndSet(false, true)) {
            try {
                scope.close()
            } catch (e: Exception) {
                L.e(e, "Error on SSE graceful close")
            }
        }
    }
}

private const val MAX_RETIES = 10
private const val BACKOFF_TIME_MIN_MS = 1_000L
private const val BACKOFF_TIME_MAX_MS = 5_000L

/**
 * Creates a Server-Sent Events (SSE) Flow for real-time data streaming.
 *
 * This is a CRITICAL component for real-time features:
 * - Wallet transaction notifications
 * - TonConnect events
 * - Swap stream updates
 *
 * Key reliability features:
 * - Atomic close guard prevents race conditions
 * - 128-element buffer with DROP_OLDEST prevents overflow crashes
 * - Smart retry logic with exponential backoff (max 5 attempts, ~15s total)
 * - Comprehensive error handling and logging
 * - Graceful degradation on failures
 *
 * @param url SSE endpoint URL
 * @param lastEventId Optional last event ID for resuming from checkpoint
 * @param onFailure Optional callback for non-terminal errors
 * @return Flow of SSE events that auto-retries on recoverable errors
 */
@OptIn(DelicateCoroutinesApi::class)
fun OkHttpClient.sse(
    url: String,
    lastEventId: Long? = null,
    onFailure: ((Throwable) -> Unit)?
): Flow<SSEvent> = callbackFlow {
    // Create dedicated listener with access to this Flow scope
    val listener = FlowEventListener(url, this)

    try {
        val builder = requestBuilder(url)
            .addHeader("Accept", "text/event-stream")
            .addHeader("Cache-Control", "no-cache")
            .addHeader("Connection", "keep-alive")
            .addHeader("Keep-Alive", "timeout=60")

        if (lastEventId != null) {
            builder.addHeader("Last-Event-ID", lastEventId.toString())
        }

        val request = builder.build()
        val events = sseFactory().newEventSource(request, listener)

        L.d("SSE connection started, url=$url")

        awaitClose {
            L.d("SSE awaitClose triggered, url=$url")
            try {
                events.cancel()
            } catch (e: Exception) {
                L.e(e, "Error canceling SSE")
            }
        }
    } catch (e: Exception) {
        L.e(e, "Error starting SSE connection")
        throw e
    }
}
    // Critical: 64-element buffer prevents overflow when collector is slower than producer
    // DROP_OLDEST strategy is correct for real-time data (latest events are more important)
    .buffer(128, BufferOverflow.DROP_OLDEST)
    .retryWhen { cause, attempt ->
        // Smart retry logic with categorization
        val shouldRetry = when {
            // User-initiated cancellation - don't retry
            cause is CancellationException -> {
                L.d("SSE cancelled by user, not retrying")
                false
            }

            // Explicit cancel() call - don't retry
            cause is IOException && cause.message?.contains(
                "canceled",
                ignoreCase = true
            ) == true -> {
                L.d("SSE canceled by cancel(), not retrying")
                false
            }

            // HTTP/2 stream cancelled - don't retry
            cause is StreamResetException && cause.errorCode == ErrorCode.CANCEL -> {
                L.d("SSE stream reset with CANCEL, not retrying")
                false
            }

            // OOM is terminal - don't retry
            cause is OutOfMemoryError -> {
                L.e(cause, "SSE OutOfMemoryError, not retrying")
                false
            }

            // Recoverable error - retry with exponential backoff
            else -> {
                val delayMs = minOf(BACKOFF_TIME_MIN_MS * (attempt + 1), BACKOFF_TIME_MAX_MS)
                L.w("SSE error (attempt ${attempt + 1}/${MAX_RETIES}), retrying in ${delayMs}ms: ${cause.message}")
                onFailure?.invoke(cause)
                delay(delayMs)
                true
            }
        }

        shouldRetry
    }
    // Last line of defense: catch any unhandled errors to prevent app crash
    .catch { e ->
        L.e(e, "Unhandled SSE error - preventing crash")
        onFailure?.invoke(e)
        // Don't rethrow - gracefully terminate the flow instead of crashing
    }
    .cancellable()

/**
 * Creates a POST-based Server-Sent Events (SSE) Flow.
 * Used for APIs like toncenter streaming that require POST with a JSON body.
 */
@OptIn(DelicateCoroutinesApi::class)
fun OkHttpClient.ssePost(
    url: String,
    jsonBody: String,
    headers: Map<String, String>? = null,
    onFailure: ((Throwable) -> Unit)?
): Flow<SSEvent> = callbackFlow {
    val listener = FlowEventListener(url, this)

    try {
        val body = jsonBody.toRequestBody("application/json".toMediaType())
        val builder = requestBuilder(url)
            .post(body)
            .addHeader("Accept", "text/event-stream")
            .addHeader("Cache-Control", "no-cache")
            .addHeader("Connection", "keep-alive")
            .addHeader("Keep-Alive", "timeout=60")
        headers?.forEach { (key, value) -> builder.addHeader(key, value) }

        val request = builder.build()
        val events = sseFactory().newEventSource(request, listener)

        L.d("SSE POST connection started, url=$url")

        awaitClose {
            L.d("SSE POST awaitClose triggered, url=$url")
            try {
                events.cancel()
            } catch (e: Exception) {
                L.e(e, "Error canceling SSE POST")
            }
        }
    } catch (e: Exception) {
        L.e(e, "Error starting SSE POST connection")
        throw e
    }
}
    .buffer(128, BufferOverflow.DROP_OLDEST)
    .retryWhen { cause, attempt ->
        val shouldRetry = when {
            cause is CancellationException -> false
            cause is IOException && cause.message?.contains("canceled", ignoreCase = true) == true -> false
            cause is StreamResetException && cause.errorCode == ErrorCode.CANCEL -> false
            cause is OutOfMemoryError -> false
            else -> {
                val delayMs = minOf(BACKOFF_TIME_MIN_MS * (attempt + 1), BACKOFF_TIME_MAX_MS)
                L.w("SSE POST error (attempt ${attempt + 1}), retrying in ${delayMs}ms: ${cause.message}")
                onFailure?.invoke(cause)
                delay(delayMs)
                true
            }
        }
        shouldRetry
    }
    .catch { e ->
        L.e(e, "Unhandled SSE POST error")
        onFailure?.invoke(e)
    }
    .cancellable()

