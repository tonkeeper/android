package com.tonapps.bus.core.aptabase

import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

private const val CONNECT_TIMEOUT_MILLIS = 10_000
private const val READ_TIMEOUT_MILLIS = 15_000
private const val MAX_RESPONSE_BODY_CHARS = 4 * 1024

interface AptabaseHttpClient {

    class Response(
        val code: Int,
        val body: String,
    )

    @Throws(IOException::class)
    fun post(url: String, headers: Map<String, String>, body: ByteArray): Response
}

class HttpUrlConnectionClient : AptabaseHttpClient {

    override fun post(url: String, headers: Map<String, String>, body: ByteArray): AptabaseHttpClient.Response {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
            connection.readTimeout = READ_TIMEOUT_MILLIS
            connection.doOutput = true
            connection.setFixedLengthStreamingMode(body.size)
            headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
            connection.outputStream.use { it.write(body) }
            val code = connection.responseCode
            val stream = if (code >= HttpURLConnection.HTTP_BAD_REQUEST) {
                connection.errorStream
            } else {
                connection.inputStream
            }
            return AptabaseHttpClient.Response(code, stream?.readBody().orEmpty())
        } finally {
            connection.disconnect()
        }
    }

    private fun InputStream.readBody(): String {
        return bufferedReader().use { reader ->
            val buffer = CharArray(MAX_RESPONSE_BODY_CHARS)
            val read = reader.read(buffer)
            if (read > 0) {
                String(buffer, 0, read)
            } else {
                ""
            }
        }
    }
}
