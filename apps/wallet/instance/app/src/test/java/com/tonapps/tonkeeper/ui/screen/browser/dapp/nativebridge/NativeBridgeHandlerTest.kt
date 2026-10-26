package com.tonapps.tonkeeper.ui.screen.browser.dapp.nativebridge

import com.tonapps.tonkeeper.ui.screen.browser.dapp.inapp.InAppBridgeContract.Effect
import com.tonapps.tonkeeper.ui.screen.browser.dapp.nativebridge.NativeBridgeProtocol.Message
import com.tonapps.tonkeeper.ui.screen.browser.dapp.nativebridge.NativeBridgeProtocol.Message.Request
import com.tonapps.tonkeeper.ui.screen.browser.dapp.nativebridge.NativeBridgeProtocol.Response
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeBridgeHandlerTest {

    private val handler = NativeBridgeHandler(TestMethods())

    @Test
    fun `an unknown method fails with the unsupported method code`() = runTest {
        handler.attach()

        handler.onMessage(Request("a", "missing.method", JSONObject()))

        val response = handler.detach().single() as Response.Failure
        assertEquals("a", response.id)
        assertEquals(NativeMethods.ERROR_UNSUPPORTED_METHOD, response.code)
    }

    @Test
    fun `a method's result is answered with the request id`() = runTest {
        handler.attach()

        handler.onMessage(Request("b", "value", JSONObject()))

        val response = handler.detach().single() as Response.Success
        assertEquals("b", response.id)
        assertEquals(42, response.result.getInt("value"))
    }

    @Test
    fun `an effect follows the answer of its method`() = runTest {
        handler.attach()
        val events = collectAnswersAndEffects()

        handler.onMessage(Request("c", "close", JSONObject()))

        assertEquals(listOf("answer c", "effect Close"), events)
    }

    @Test
    fun `a failed method emits no effect`() = runTest {
        handler.attach()
        val events = collectAnswersAndEffects()

        handler.onMessage(Request("d", "broken", JSONObject()))

        assertEquals(listOf("answer d"), events)
    }

    @Test
    fun `detach hands back answers nobody collected`() = runTest {
        handler.attach()

        handler.onMessage(Request("e", "value", JSONObject()))
        handler.onMessage(Request("f", "value", JSONObject()))

        assertEquals(listOf("e", "f"), handler.detach().map { (it as Response.Success).id })
    }

    @Test
    fun `a new attach does not replay answers of the previous one`() = runTest {
        handler.attach()
        handler.onMessage(Request("g", "value", JSONObject()))
        handler.detach()

        handler.attach()

        assertTrue(handler.detach().isEmpty())
    }

    @Test
    fun `a ready document gets the events it missed, not an answer`() = runTest {
        handler.attach()

        handler.onMessage(Message.Ready)

        val event = handler.detach().single() as Response.Event
        assertEquals("missed", event.name)
    }

    private fun TestScope.collectAnswersAndEffects(): List<String> {
        val events = mutableListOf<String>()
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        backgroundScope.launch(dispatcher) {
            handler.responses.collect { events += "answer ${(it as? Response.Success)?.id ?: (it as Response.Failure).id}" }
        }
        backgroundScope.launch(dispatcher) {
            handler.effects.collect { events += "effect ${it::class.simpleName}" }
        }
        return events
    }

    private class TestMethods : NativeMethods() {

        override fun onDocumentReady(): List<NativeEvent> = listOf(NativeEvent("missed", JSONObject()))

        override fun invoke(method: String, params: JSONObject): NativeMethodResult? {
            return when (method) {
                "value" -> NativeMethodResult.Success(JSONObject().put("value", 42))
                "close" -> NativeMethodResult.Success(effect = Effect.Close)
                "broken" -> NativeMethodResult.Failure(ERROR_INVALID_PARAMS, "Broken")
                else -> null
            }
        }
    }
}