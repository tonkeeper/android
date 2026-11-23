package com.tonapps.bus.screenrender

import java.io.IOException
import java.net.SocketTimeoutException

object ScreenRenderErrors {

    // Matched by name: lib/bus does not depend on the API clients. Release builds keep these names through
    // `-keepnames` rules in instance/main/proguard-rules.pro.
    private val serverErrorNames = setOf("ClientException", "ServerException")
    private val decodeErrorNames = setOf("SerializationException", "MissingFieldException", "JsonDecodingException", "JSONException")

    /**
     * The kind of a load failure. A lost connection is a generic `Network` here; the reporter turns it into
     * `Offline` when the device has no connection.
     */
    fun kind(error: Throwable): ScreenRenderErrorKind {
        val chain = generateSequence(error) { it.cause }.take(8).toList()
        return when {
            chain.any { it is SocketTimeoutException } -> ScreenRenderErrorKind.Timeout
            chain.any { it.javaClass.simpleName in serverErrorNames } -> ScreenRenderErrorKind.Server
            chain.any { it.javaClass.simpleName in decodeErrorNames } -> ScreenRenderErrorKind.Decode
            chain.any { it is IOException } -> ScreenRenderErrorKind.Network
            else -> ScreenRenderErrorKind.Unknown
        }
    }
}
