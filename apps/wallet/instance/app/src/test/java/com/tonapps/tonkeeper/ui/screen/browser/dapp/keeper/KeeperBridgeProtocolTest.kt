package com.tonapps.tonkeeper.ui.screen.browser.dapp.keeper

import com.tonapps.tonkeeper.ui.screen.browser.dapp.keeper.KeeperBridgeProtocol.AppSettings
import com.tonapps.tonkeeper.ui.screen.browser.dapp.keeper.KeeperBridgeProtocol.Credentials
import com.tonapps.tonkeeper.ui.screen.browser.dapp.keeper.KeeperBridgeProtocol.ErrorCode
import com.tonapps.tonkeeper.ui.screen.browser.dapp.keeper.KeeperBridgeProtocol.Request
import com.tonapps.tonkeeper.ui.screen.browser.dapp.keeper.KeeperBridgeProtocol.Response
import com.tonapps.webview.bridge.InAppBridgeContract.HapticStyle
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeeperBridgeProtocolTest {

    private val protocol = KeeperBridgeProtocol()

    @Test
    fun `get-data is parsed with its query id and the page host`() {
        val request = protocol.parse("""{"type":"get-data","queryId":"q1"}""", TRUSTED_HOST, null) as Request.GetData

        assertEquals("q1", request.queryId)
        assertEquals(TRUSTED_HOST, request.host)
    }

    @Test
    fun `refresh-data carries the access token the page failed with`() {
        val request = protocol.parse(
            """{"type":"refresh-data","queryId":"q2","accessToken":"expired"}""",
            TRUSTED_HOST,
            null,
        ) as Request.RefreshData

        assertEquals("q2", request.queryId)
        assertEquals("expired", request.expiredAccessToken)
    }

    @Test
    fun `refresh-data without an access token is rejected as invalid params`() {
        val messages = listOf(
            """{"type":"refresh-data","queryId":"q"}""",
            """{"type":"refresh-data","queryId":"q","accessToken":""}""",
            """{"type":"refresh-data","queryId":"q","accessToken":null}""",
            """{"type":"refresh-data","queryId":"q","accessToken":123}""",
        )

        messages.forEach { message ->
            assertRejected(protocol.parse(message, TRUSTED_HOST, null), ErrorCode.InvalidParams)
        }
    }

    @Test
    fun `close is parsed`() {
        assertTrue(protocol.parse("""{"type":"close","queryId":"q3"}""", TRUSTED_HOST, null) is Request.Close)
    }

    @Test
    fun `haptic is parsed with each style`() {
        val styles = mapOf(
            "selection" to HapticStyle.Selection,
            "light" to HapticStyle.Light,
            "medium" to HapticStyle.Medium,
            "heavy" to HapticStyle.Heavy,
            "success" to HapticStyle.Success,
            "warning" to HapticStyle.Warning,
            "error" to HapticStyle.Error,
        )

        styles.forEach { (style, expected) ->
            val request = protocol.parse("""{"type":"haptic","queryId":"q","style":"$style"}""", TRUSTED_HOST, null)

            assertEquals(style, expected, (request as Request.Haptic).style)
        }
    }

    @Test
    fun `haptic without a known style is rejected as invalid params`() {
        val messages = listOf(
            """{"type":"haptic","queryId":"q"}""",
            """{"type":"haptic","queryId":"q","style":"rigid"}""",
            """{"type":"haptic","queryId":"q","style":"LIGHT"}""",
            """{"type":"haptic","queryId":"q","style":1}""",
        )

        messages.forEach { message ->
            assertRejected(protocol.parse(message, TRUSTED_HOST, null), ErrorCode.InvalidParams)
        }
    }

    @Test
    fun `set-close-button carries the visibility`() {
        val hide = protocol.parse("""{"type":"set-close-button","queryId":"q","visible":false}""", TRUSTED_HOST, null)
        val show = protocol.parse("""{"type":"set-close-button","queryId":"q","visible":true}""", TRUSTED_HOST, null)

        assertFalse((hide as Request.SetCloseButton).visible)
        assertTrue((show as Request.SetCloseButton).visible)
    }

    @Test
    fun `set-close-button without a boolean visibility is rejected as invalid params`() {
        val messages = listOf(
            """{"type":"set-close-button","queryId":"q"}""",
            """{"type":"set-close-button","queryId":"q","visible":"false"}""",
            """{"type":"set-close-button","queryId":"q","visible":0}""",
            """{"type":"set-close-button","queryId":"q","visible":null}""",
        )

        messages.forEach { message ->
            assertRejected(protocol.parse(message, TRUSTED_HOST, null), ErrorCode.InvalidParams)
        }
    }

    @Test
    fun `share carries the text`() {
        val request = protocol.parse(
            """{"type":"share","queryId":"q","text":"Join me on Keeper"}""",
            TRUSTED_HOST,
            null,
        ) as Request.Share

        assertEquals("q", request.queryId)
        assertEquals("Join me on Keeper", request.text)
    }

    @Test
    fun `share without text is rejected as invalid params`() {
        val messages = listOf(
            """{"type":"share","queryId":"q"}""",
            """{"type":"share","queryId":"q","text":""}""",
            """{"type":"share","queryId":"q","text":" "}""",
            """{"type":"share","queryId":"q","text":null}""",
            """{"type":"share","queryId":"q","text":42}""",
        )

        messages.forEach { message ->
            assertRejected(protocol.parse(message, TRUSTED_HOST, null), ErrorCode.InvalidParams)
        }
    }

    @Test
    fun `share from a sender outside the keeperwallet subdomains is forbidden`() {
        val request = protocol.parse("""{"type":"share","queryId":"q","text":"hi"}""", "example.org", null)

        assertRejected(request, ErrorCode.Forbidden)
    }

    @Test
    fun `analytics track carries the event, its params and the sending host`() {
        val request = protocol.parse(
            """{"type":"analytics.track","queryId":"q","event":"loyalty_open","params":{"source":"banner","count":2}}""",
            "Loyalty.KeeperWallet.com",
            null,
        ) as Request.AnalyticsTrack

        assertEquals("loyalty_open", request.event)
        assertEquals(mapOf<String, Any?>("source" to "banner", "count" to 2), request.params)
        assertEquals("loyalty.keeperwallet.com", request.provider)
    }

    @Test
    fun `analytics track without params tracks the event alone`() {
        val request = protocol.parse(
            """{"type":"analytics.track","queryId":"q","event":"loyalty_open"}""",
            TRUSTED_HOST,
            null,
        ) as Request.AnalyticsTrack

        assertTrue(request.params.isEmpty())
    }

    @Test
    fun `analytics track without an event is rejected as invalid params`() {
        val messages = listOf(
            """{"type":"analytics.track","queryId":"q"}""",
            """{"type":"analytics.track","queryId":"q","event":" "}""",
            """{"type":"analytics.track","queryId":"q","event":42}""",
        )

        messages.forEach { message ->
            assertRejected(protocol.parse(message, TRUSTED_HOST, null), ErrorCode.InvalidParams)
        }
    }

    @Test
    fun `navigate and unknown methods are unsupported`() {
        val navigate = """{"type":"navigate","queryId":"q","payload":{"screen":"wallet"}}"""

        assertRejected(protocol.parse(navigate, TRUSTED_HOST, null), ErrorCode.UnsupportedMethod)
        assertRejected(protocol.parse("""{"type":"open-url","queryId":"q"}""", TRUSTED_HOST, null), ErrorCode.UnsupportedMethod)
    }

    @Test
    fun `a message without a type is invalid params`() {
        assertRejected(protocol.parse("""{"queryId":"q"}""", TRUSTED_HOST, null), ErrorCode.InvalidParams)
    }

    @Test
    fun `a message without a usable query id is ignored`() {
        val messages = listOf(
            "not json",
            "[]",
            """{"type":"get-data"}""",
            """{"type":"get-data","queryId":" "}""",
            """{"type":"get-data","queryId":123}""",
        )

        messages.forEach { message ->
            assertNull(message, protocol.parse(message, TRUSTED_HOST, null))
        }
    }

    @Test
    fun `a sender outside the keeperwallet subdomains is forbidden`() {
        val hosts = listOf("keeperwallet.com", "fakekeeperwallet.com", "keeperwallet.com.example.org", "example.org", "")

        hosts.forEach { host ->
            assertRejected(protocol.parse("""{"type":"get-data","queryId":"q"}""", host, null), ErrorCode.Forbidden)
        }
    }

    @Test
    fun `keeperwallet subdomains are trusted hosts`() {
        assertTrue(isKeeperDAppHost("trump.keeperwallet.com"))
        assertTrue(isKeeperDAppHost("battery.keeperwallet.com"))
        assertTrue(isKeeperDAppHost("A.B.KEEPERWALLET.COM"))
        assertFalse(isKeeperDAppHost("keeperwallet.com"))
        assertFalse(isKeeperDAppHost("fakekeeperwallet.com"))
        assertFalse(isKeeperDAppHost("keeperwallet.com.example.org"))
        assertFalse(isKeeperDAppHost(null))
    }

    @Test
    fun `get-data answer carries credentials, theme and language`() {
        val response = Response.Data(
            queryId = "q",
            credentials = Credentials("wallet", "device", "proof"),
            appSettings = AppSettings(theme = "deepBlue", language = "ru"),
        )

        val json = JSONObject(protocol.encode(response))

        assertEquals("q", json.getString("queryId"))
        assertTrue(json.isNull("error"))
        val payload = json.getJSONObject("payload")
        assertEquals("wallet", payload.getString("walletId"))
        assertEquals("device", payload.getString("deviceToken"))
        assertEquals("proof", payload.getString("walletToken"))
        assertEquals("deepBlue", payload.getString("theme"))
        assertEquals("ru", payload.getString("language"))
    }

    @Test
    fun `get-data answer carries the referral code when there is one`() {
        val response = Response.Data(
            queryId = "q",
            credentials = Credentials("wallet", "device", "proof"),
            appSettings = AppSettings(theme = "dark", language = "en"),
            referralCode = "UQJS7K21w",
        )

        val payload = JSONObject(protocol.encode(response)).getJSONObject("payload")

        assertEquals("UQJS7K21w", payload.getString("referralCode"))
    }

    @Test
    fun `get-data answer without a referral code has no referralCode key`() {
        val response = Response.Data(
            queryId = "q",
            credentials = Credentials("wallet", "device", "proof"),
            appSettings = AppSettings(theme = "dark", language = "en"),
        )

        val payload = JSONObject(protocol.encode(response)).getJSONObject("payload")

        assertFalse(payload.has("referralCode"))
    }

    @Test
    fun `refresh-data answer carries only the credentials`() {
        val response = Response.Data("q", Credentials("wallet", "device", "proof"), appSettings = null)

        val payload = JSONObject(protocol.encode(response)).getJSONObject("payload")

        assertEquals(setOf("walletId", "deviceToken", "walletToken"), payload.keys().asSequence().toSet())
    }

    @Test
    fun `a done request is acknowledged with an empty payload`() {
        val json = JSONObject(protocol.encode(Response.Done("q")))

        assertEquals(0, json.getJSONObject("payload").length())
        assertTrue(json.isNull("error"))
    }

    @Test
    fun `a failure has no payload and names its code`() {
        val json = JSONObject(protocol.encode(Response.Failure("q", ErrorCode.Unauthorized)))

        assertEquals("q", json.getString("queryId"))
        assertTrue(json.isNull("payload"))
        assertEquals("UNAUTHORIZED", json.getJSONObject("error").getString("code"))
        assertEquals("Authorization failed", json.getJSONObject("error").getString("message"))
    }

    @Test
    fun `logs name the method and the query, never the token`() {
        val request = protocol.parse(
            """{"type":"refresh-data","queryId":"q","accessToken":"private-token"}""",
            TRUSTED_HOST,
            null,
        )!!

        val described = protocol.describeMessage(request)

        assertEquals("refresh-data #q", described)
        assertFalse(described.contains("private-token"))
    }

    @Test
    fun `logs name an analytics call without its event or params`() {
        val request = protocol.parse(
            """{"type":"analytics.track","queryId":"q","event":"loyalty_open","params":{"user":"private"}}""",
            TRUSTED_HOST,
            null,
        )!!

        assertEquals("analytics.track #q", protocol.describeMessage(request))
    }

    @Test
    fun `logs name a share call without its text`() {
        val request = protocol.parse(
            """{"type":"share","queryId":"q","text":"app.keeperwallet.com/r/PRIVATE"}""",
            TRUSTED_HOST,
            null,
        )!!

        assertEquals("share #q", protocol.describeMessage(request))
    }

    private fun assertRejected(request: Request?, error: ErrorCode) {
        assertTrue("$request", request is Request.Rejected)
        assertEquals(error, (request as Request.Rejected).error)
    }

    private companion object {
        private const val TRUSTED_HOST = "trump.keeperwallet.com"
    }
}
