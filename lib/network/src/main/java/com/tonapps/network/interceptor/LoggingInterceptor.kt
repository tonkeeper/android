package com.tonapps.network.interceptor

import android.os.SystemClock
import com.tonapps.log.L
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.internal.http.promisesBody
import okio.Buffer
import java.io.PrintWriter
import java.io.StringWriter
import java.util.concurrent.atomic.AtomicInteger

class LoggingInterceptor : Interceptor {

    private val requestIdGenerator = AtomicInteger(1)

    private val prefixer = LoggingPrefixer()
    private val prefix = ThreadLocal<String>()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        return runBlocking {
            prefix.set(prefixer.getPrefix())
            val requestId = requestIdGenerator.getAndIncrement()
            val response = interceptWithDetailedLog(requestId, request, chain)

            response
        }
    }

    private fun interceptWithDetailedLog(requestId: Int, request: Request, chain: Interceptor.Chain): Response {
        val requestLog = mutableListOf<String>()
        requestLog.add("----> [$requestId] =============== Request ===============")
        requestLog.add("${request.method} ${request.url}")

        if (request.headers.size > 0) {
            request.headers.forEach { (header, values) ->
                if (header == "Authorization") {
                    requestLog.add("Authorization: <hidden>")
                } else {
                    requestLog.add("$header: $values")
                }
            }
        }

        when (val requestBody = request.body) {
            null -> requestLog.add("<empty>")
            else -> {
                requestLog.add("")
                requestLog.add("Request body:")

                val buffer = Buffer()
                requestBody.writeTo(buffer)
                requestLog.add(buffer.readUtf8())
            }
        }

        requestLog.add("----> [$requestId] End of request")
        netLog(requestLog)

        try {
            val timeStartMs = SystemClock.elapsedRealtime()
            val response = chain.proceed(request)
            val timeEndMs = SystemClock.elapsedRealtime()
            val duration = timeEndMs - timeStartMs

            val responseLog = mutableListOf<String>()
            responseLog.add("<---- [$requestId] =============== Response ===============")
            responseLog.add("${response.code} ${response.message} ${request.url} (${duration}ms)")

            if (response.headers.size > 0) {
                response.headers.forEach { (header, values) ->
                    responseLog.add("$header: $values")
                }
            }

            val responseBody = response.body

            responseLog.add("")
            responseLog.add("Response body:")

            val isGzip = "gzip".equals(response.header("content-encoding"), ignoreCase = true)
            val isStreaming = "text/event-stream".equals(
                response.header("content-type")?.substringBefore(';'),
                ignoreCase = true
            )
            if (response.promisesBody() && !isGzip && !isStreaming) {
                val source = responseBody.source()
                source.request(Long.MAX_VALUE) // Buffer the entire body.
                val buffer = source.buffer

                val response = buffer.clone().readString(Charsets.UTF_8)
                responseLog.add(response)
            } else if (isStreaming) {
                responseLog.add("<streaming: text/event-stream>")
            } else {
                responseLog.add("<empty>")
            }

            responseLog.add("<---- [$requestId] End of Response")
            netLog(responseLog)

            return response
        } catch (th: Throwable) {
            logError(requestId, request, th)
            throw th
        }
    }

    private fun logError(requestId: Int, request: Request, th: Throwable) {
        val responseLog = mutableListOf<String>().apply {
            add("<---- [$requestId] Response")
            add(request.url.toString())
            addAll(th.getStackTraceString().lines())
            add("<---- [$requestId] End of Response")
        }
        netErr(responseLog)
    }

    private fun netLog(lines: List<String>) {
        val log = lines.joinToString("\n")
        if (log.isNotBlank()) L.d("NetLog", "${prefix.get()} ${log.trimEnd()}")
    }

    private fun netErr(lines: List<String>) {
        val log = lines.joinToString("\n")
        if (log.isNotBlank()) L.e("NetLog", "${prefix.get()} ${log.trimEnd()}")
    }

    private fun Throwable?.getStackTraceString(): String {
        if (this == null) return ""
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        try {
            this.printStackTrace(pw)
            pw.flush()
            return sw.toString()
        } finally {
            pw.close()
            sw.close()
        }
    }

    private class LoggingPrefixer {

        private val startEmoji: Int = 129292 // 🤌
        private val endEmoji: Int = 129535 // 🧾
        private var lastUsedEmoji = startEmoji

        private val mutex = Mutex()

        suspend fun getPrefix(): String {
            mutex.withLock {
                if (lastUsedEmoji > endEmoji) {
                    lastUsedEmoji = startEmoji
                }
                return String(Character.toChars(lastUsedEmoji++))
            }
        }
    }
}
