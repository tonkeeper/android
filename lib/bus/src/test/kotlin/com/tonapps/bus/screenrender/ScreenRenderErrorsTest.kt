package com.tonapps.bus.screenrender

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class ScreenRenderErrorsTest {

    private class ServerException : RuntimeException()
    private class SerializationException : IllegalArgumentException()

    @Test
    fun timeoutWinsOverOtherIoErrors() {
        assertEquals(ScreenRenderErrorKind.Timeout, ScreenRenderErrors.kind(SocketTimeoutException()))
        assertEquals(ScreenRenderErrorKind.Timeout, ScreenRenderErrors.kind(IllegalStateException(SocketTimeoutException())))
    }

    @Test
    fun connectionErrorsAreNetwork() {
        assertEquals(ScreenRenderErrorKind.Network, ScreenRenderErrors.kind(UnknownHostException()))
        assertEquals(ScreenRenderErrorKind.Network, ScreenRenderErrors.kind(IOException()))
    }

    @Test
    fun apiClientErrorsAreServerByName() {
        assertEquals(ScreenRenderErrorKind.Server, ScreenRenderErrors.kind(ServerException()))
        assertEquals(ScreenRenderErrorKind.Server, ScreenRenderErrors.kind(RuntimeException(ServerException())))
    }

    @Test
    fun parsingErrorsAreDecode() {
        assertEquals(ScreenRenderErrorKind.Decode, ScreenRenderErrors.kind(SerializationException()))
    }

    @Test
    fun anythingElseIsUnknown() {
        assertEquals(ScreenRenderErrorKind.Unknown, ScreenRenderErrors.kind(Exception("API error")))
    }
}
