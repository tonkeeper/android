package com.tonapps.tonkeeper.ui.screen.browser.dapp.keeper

import com.tonapps.test.silenceLog
import com.tonapps.tonkeeper.ui.screen.browser.dapp.keeper.KeeperBridgeProtocol.AppSettings
import com.tonapps.tonkeeper.ui.screen.browser.dapp.keeper.KeeperBridgeProtocol.Credentials
import com.tonapps.tonkeeper.ui.screen.browser.dapp.keeper.KeeperBridgeProtocol.ErrorCode
import com.tonapps.tonkeeper.ui.screen.browser.dapp.keeper.KeeperBridgeProtocol.Request
import com.tonapps.tonkeeper.ui.screen.browser.dapp.keeper.KeeperBridgeProtocol.Response
import com.tonapps.webview.bridge.InAppBridgeContract.Effect
import com.tonapps.webview.bridge.InAppBridgeContract.HapticStyle
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class KeeperBridgeHandlerTest {

    private val credentials = Credentials("wallet", "device", "proof")

    private val settings = AppSettings(theme = "dark", language = "en")

    @Before
    fun setUp() {
        silenceLog()
    }

    @Test
    fun `get-data answers the credentials with the theme and language`() = runTest {
        val handler = handler(backgroundScope)
        handler.attach()

        handler.onMessage(Request.GetData("q1", LOYALTY_HOST))
        runCurrent()

        val response = handler.detach().single() as Response.Data
        assertEquals("q1", response.queryId)
        assertEquals("wallet", response.credentials.walletId)
        assertEquals("dark", response.appSettings?.theme)
        assertEquals("en", response.appSettings?.language)
    }

    @Test
    fun `refresh-data renews with the expired token and answers only the credentials`() = runTest {
        val expiredTokens = mutableListOf<String?>()
        val handler = handler(backgroundScope, loadCredentials = { expiredTokens += it; credentials })
        handler.attach()

        handler.onMessage(Request.RefreshData("q2", "expired"))
        runCurrent()

        val response = handler.detach().single() as Response.Data
        assertEquals(listOf<String?>("expired"), expiredTokens)
        assertNull(response.appSettings)
    }

    @Test
    fun `no credentials is unauthorized`() = runTest {
        val handler = handler(backgroundScope, loadCredentials = { null })
        handler.attach()

        handler.onMessage(Request.GetData("q3", LOYALTY_HOST))
        runCurrent()

        assertFailure(handler.detach().single(), "q3", ErrorCode.Unauthorized)
    }

    @Test
    fun `a failing credentials load is unauthorized`() = runTest {
        val handler = handler(backgroundScope, loadCredentials = { error("vault is locked") })
        handler.attach()

        handler.onMessage(Request.RefreshData("q4", "expired"))
        runCurrent()

        assertFailure(handler.detach().single(), "q4", ErrorCode.Unauthorized)
    }

    @Test
    fun `a rejected request is answered with its error`() = runTest {
        val handler = handler(backgroundScope)
        handler.attach()

        handler.onMessage(Request.Rejected("q5", ErrorCode.Forbidden))

        assertFailure(handler.detach().single(), "q5", ErrorCode.Forbidden)
    }

    @Test
    fun `close is acknowledged before the screen is closed`() = runTest {
        val handler = handler(backgroundScope)
        handler.attach()
        val events = collectAnswersAndEffects(handler)

        handler.onMessage(Request.Close("q6"))

        assertEquals(listOf("answer Done #q6", "effect Close"), events)
    }

    @Test
    fun `haptic is acknowledged and played by the screen`() = runTest {
        val handler = handler(backgroundScope)
        handler.attach()
        val events = collectAnswersAndEffects(handler)

        handler.onMessage(Request.Haptic("q8", HapticStyle.Warning))

        assertEquals(listOf("answer Done #q8", "effect ${Effect.Haptic(HapticStyle.Warning)}"), events)
    }

    @Test
    fun `share is acknowledged and opened by the screen`() = runTest {
        val handler = handler(backgroundScope)
        handler.attach()
        val events = collectAnswersAndEffects(handler)

        handler.onMessage(Request.Share("q12", "Join me on Keeper"))

        assertEquals(listOf("answer Done #q12", "effect ${Effect.Share("Join me on Keeper")}"), events)
    }

    @Test
    fun `set-close-button is acknowledged and applied by the screen`() = runTest {
        val handler = handler(backgroundScope)
        handler.attach()
        val events = collectAnswersAndEffects(handler)

        handler.onMessage(Request.SetCloseButton("q9", visible = false))
        handler.onMessage(Request.SetCloseButton("q10", visible = true))

        assertEquals(
            listOf(
                "answer Done #q9",
                "effect ${Effect.CloseButtonVisibility(visible = false)}",
                "answer Done #q10",
                "effect ${Effect.CloseButtonVisibility(visible = true)}",
            ),
            events,
        )
    }

    @Test
    fun `analytics track is sent marked with the page host and acknowledged`() = runTest {
        val tracked = mutableListOf<Pair<String, Map<String, Any>>>()
        val handler = handler(backgroundScope, track = { event, params -> tracked += event to params })
        handler.attach()
        val events = collectAnswersAndEffects(handler)

        handler.onMessage(
            Request.AnalyticsTrack(
                queryId = "q11",
                event = "loyalty_open",
                params = mapOf(
                    "source" to "banner",
                    "address" to "0x0eE3CdEE4f010BF6c49aF86E9e67ef2bc80a508b",
                    "nested" to JSONObject(),
                ),
                provider = "loyalty.keeperwallet.com",
            )
        )

        assertEquals(listOf("answer Done #q11"), events)
        assertEquals(
            listOf(
                "loyalty_open" to mapOf<String, Any>(
                    "source" to "banner",
                    "address" to "<hex>",
                    "isExternalWebView" to 1,
                    "eventProvider" to "loyalty.keeperwallet.com",
                )
            ),
            tracked,
        )
    }

    @Test
    fun `a request still loading when the page goes is never answered`() = runTest {
        val loaded = CompletableDeferred<Credentials?>()
        val handler = handler(backgroundScope, loadCredentials = { loaded.await() })
        handler.attach()
        handler.onMessage(Request.GetData("q7", LOYALTY_HOST))
        runCurrent()

        assertTrue(handler.detach().isEmpty())
        handler.attach()
        loaded.complete(credentials)
        runCurrent()

        assertTrue(handler.detach().isEmpty())
    }

    @Test
    fun `the screen closes once its wallet is no longer active`() = runTest {
        val walletDeactivated = MutableSharedFlow<Unit>()
        val handler = handler(backgroundScope, walletDeactivated = walletDeactivated)
        handler.attach()
        val events = collectAnswersAndEffects(handler)
        runCurrent()

        walletDeactivated.emit(Unit)
        runCurrent()

        assertEquals(listOf("effect Close"), events)
    }

    @Test
    fun `get-data answers the referral code of the page host`() = runTest {
        val hosts = mutableListOf<String>()
        val handler = handler(backgroundScope, referralCode = { hosts += it; "UQJS7K21w" })
        handler.attach()

        handler.onMessage(Request.GetData("q8", LOYALTY_HOST))
        runCurrent()

        val response = handler.detach().single() as Response.Data
        assertEquals(listOf(LOYALTY_HOST), hosts)
        assertEquals("UQJS7K21w", response.referralCode)
    }

    @Test
    fun `refresh-data never takes the referral code`() = runTest {
        val hosts = mutableListOf<String>()
        val handler = handler(backgroundScope, referralCode = { hosts += it; "UQJS7K21w" })
        handler.attach()

        handler.onMessage(Request.RefreshData("q9", "expired"))
        runCurrent()

        val response = handler.detach().single() as Response.Data
        assertTrue(hosts.isEmpty())
        assertNull(response.referralCode)
    }

    @Test
    fun `an unauthorized get-data leaves the referral code for the next answer`() = runTest {
        val hosts = mutableListOf<String>()
        val handler = handler(backgroundScope, loadCredentials = { null }, referralCode = { hosts += it; "UQJS7K21w" })
        handler.attach()

        handler.onMessage(Request.GetData("q10", LOYALTY_HOST))
        runCurrent()

        assertFailure(handler.detach().single(), "q10", ErrorCode.Unauthorized)
        assertTrue(hosts.isEmpty())
    }

    private fun handler(
        scope: CoroutineScope,
        loadCredentials: suspend (String?) -> Credentials? = { credentials },
        track: (String, MutableMap<String, Any>) -> Unit = { _, _ -> },
        referralCode: suspend (String) -> String? = { null },
        walletDeactivated: Flow<Unit> = emptyFlow(),
    ): KeeperBridgeHandler {
        return KeeperBridgeHandler(
            scope = scope,
            loadCredentials = loadCredentials,
            appSettings = { settings },
            track = track,
            referralCode = referralCode,
            walletDeactivated = walletDeactivated,
        )
    }

    private fun TestScope.collectAnswersAndEffects(handler: KeeperBridgeHandler): List<String> {
        val events = mutableListOf<String>()
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        backgroundScope.launch(dispatcher) {
            handler.responses.collect { events += "answer ${it::class.simpleName} #${it.queryId}" }
        }
        backgroundScope.launch(dispatcher) {
            handler.effects.collect { events += "effect $it" }
        }
        return events
    }

    private fun assertFailure(response: Response, queryId: String, error: ErrorCode) {
        val failure = response as Response.Failure
        assertEquals(queryId, failure.queryId)
        assertEquals(error, failure.error)
    }

    private companion object {
        private const val LOYALTY_HOST = "loyalty.keeperwallet.com"
    }
}
