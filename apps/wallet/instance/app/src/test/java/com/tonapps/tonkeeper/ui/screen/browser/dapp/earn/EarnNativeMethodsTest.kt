package com.tonapps.tonkeeper.ui.screen.browser.dapp.earn

import com.tonapps.tonkeeper.ui.screen.browser.dapp.inapp.InAppBridgeContract.Effect
import com.tonapps.tonkeeper.ui.screen.browser.dapp.nativebridge.NativeBridgeProtocol
import com.tonapps.tonkeeper.ui.screen.browser.dapp.nativebridge.NativeMethodResult
import com.tonapps.tonkeeper.ui.screen.browser.dapp.nativebridge.NativeMethods
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class EarnNativeMethodsTest {

    private var theme = "dark"

    private var tracked: Pair<String, Map<String, Any>>? = null

    private val methods = EarnNativeMethods(
        onTrack = { event, params -> tracked = event to params },
        currentTheme = { theme },
    )

    @Test
    fun `navigateBack closes the screen`() {
        val result = methods.handle("ui.navigateBack", JSONObject()) as NativeMethodResult.Success
        assertSame(Effect.Close, result.effect)
    }

    @Test
    fun `close is answered like navigateBack`() {
        val result = methods.handle("ui.close", JSONObject()) as NativeMethodResult.Success
        assertSame(Effect.Close, result.effect)
    }

    @Test
    fun `tracking without an event name is refused`() {
        val result = methods.handle("analytics.track", JSONObject()) as NativeMethodResult.Failure
        assertEquals(NativeMethods.ERROR_INVALID_PARAMS, result.code)
    }

    @Test
    fun `tracking forwards scalar params only`() {
        val params = JSONObject()
            .put("event", "earn_open")
            .put(
                "params",
                JSONObject()
                    .put("source", "carousel")
                    .put("count", 2)
                    .put("nested", JSONObject().put("ignored", true)),
            )

        methods.handle("analytics.track", params)

        assertEquals("earn_open", tracked?.first)
        assertEquals(
            mapOf<String, Any>(
                "source" to "carousel",
                "count" to 2,
                "isExternalWebView" to 1,
                "eventProvider" to EarnPageEvent.PROVIDER,
            ),
            tracked?.second,
        )
    }

    @Test
    fun `a document injected with the current theme gets no event`() {
        methods.injectTheme()

        assertTrue(methods.onDocumentReady().isEmpty())
    }

    @Test
    fun `a document injected before a theme switch catches up when ready`() {
        methods.injectTheme()
        theme = "light"

        val event = methods.onDocumentReady().single()

        assertEquals(NativeBridgeProtocol.EVENT_THEME_CHANGED, event.name)
        assertEquals("light", event.payload.getString("theme"))
    }

    @Test
    fun `a theme switch on a ready document is sent once`() = runTest {
        methods.injectTheme()
        methods.onDocumentReady()
        val events = mutableListOf<String>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            methods.events.collect { events += it.payload.getString("theme") }
        }

        theme = "light"
        methods.onThemeChanged()
        methods.onThemeChanged()

        assertEquals(listOf("light"), events)
    }

    @Test
    fun `no theme event before a document is ready`() = runTest {
        methods.injectTheme()
        theme = "light"
        val events = mutableListOf<String>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            methods.events.collect { events += it.name }
        }

        methods.onThemeChanged()

        assertTrue(events.isEmpty())
    }
}
