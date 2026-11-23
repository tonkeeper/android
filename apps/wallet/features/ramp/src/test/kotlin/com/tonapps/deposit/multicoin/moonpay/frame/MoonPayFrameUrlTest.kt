package com.tonapps.deposit.multicoin.moonpay.frame

import com.tonapps.deposit.multicoin.moonpay.api.MoonPayEnvironment
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MoonPayFrameUrlTest {

    @Test
    fun `check-connection escapes tokens`() {
        val url = MoonPayFrame.CheckConnection(sessionToken = "sess+1/=", publicKeyHex = "abcd")
            .url(MoonPayEnvironment.Production, channelId = "ch_1")

        val parsed = url.toHttpUrl()
        assertEquals("https", parsed.scheme)
        assertEquals("platform.moonpay.com", parsed.host)
        assertEquals("/platform/v1/check-connection", parsed.encodedPath)
        assertEquals(mapOf("sessionToken" to "sess+1/=", "publicKey" to "abcd", "channelId" to "ch_1"), parsed.parameters())
        assertTrue(url, url.contains("sessionToken=sess%2B1%2F%3D"))
    }

    @Test
    fun `check-connection passes skipKyc`() {
        val url = MoonPayFrame.CheckConnection(sessionToken = "s", publicKeyHex = "p", skipKyc = true)
            .url(MoonPayEnvironment.Production, channelId = "c")

        assertEquals("true", url.toHttpUrl().queryParameter("skipKyc"))
    }

    @Test
    fun `connect`() {
        val url = MoonPayFrame.Connect(clientToken = "client", publicKeyHex = "pub", theme = MoonPayTheme.Dark)
            .url(MoonPayEnvironment.Production, channelId = "ch_2")
            .toHttpUrl()

        assertEquals("/platform/v1/connect", url.encodedPath)
        assertEquals(
            mapOf("clientToken" to "client", "publicKey" to "pub", "theme" to "dark", "channelId" to "ch_2"),
            url.parameters(),
        )
    }

    @Test
    fun `widget adds the buy flow`() {
        val url = MoonPayFrame.Widget(
            clientToken = "client",
            quoteSignature = "sig",
            externalTransactionId = "ext-1",
            theme = MoonPayTheme.Light,
        ).url(MoonPayEnvironment.Production, channelId = "ch_3").toHttpUrl()

        assertEquals("/platform/v1/widget", url.encodedPath)
        assertEquals(
            mapOf(
                "flow" to "buy",
                "clientToken" to "client",
                "quoteSignature" to "sig",
                "externalTransactionId" to "ext-1",
                "theme" to "light",
                "channelId" to "ch_3",
            ),
            url.parameters(),
        )
    }

    @Test
    fun `buy omits missing parameters`() {
        val url = MoonPayFrame.Buy(clientToken = "client", signature = "sig")
            .url(MoonPayEnvironment.Production, channelId = "ch_4")
            .toHttpUrl()

        assertEquals("/platform/v1/buy", url.encodedPath)
        assertEquals(mapOf("clientToken" to "client", "signature" to "sig", "channelId" to "ch_4"), url.parameters())
    }

    @Test
    fun `add-card`() {
        val url = MoonPayFrame.AddCard(clientToken = "client", theme = null)
            .url(MoonPayEnvironment.Production, channelId = "ch_6")
            .toHttpUrl()

        assertEquals("/platform/v1/add-card", url.encodedPath)
        assertEquals(mapOf("clientToken" to "client", "channelId" to "ch_6"), url.parameters())
    }

    @Test
    fun `challenge keeps the issued path and query`() {
        val issued = "https://platform.moonpay.com/platform/v1/challenge?challengeToken=abc%2Bdef&flow=buy"

        val url = MoonPayFrame.Challenge(url = issued, clientToken = "client", theme = MoonPayTheme.Dark, resume = false)
            .url(MoonPayEnvironment.Production, channelId = "ch_7")

        assertTrue(url, url.startsWith("$issued&"))
        assertEquals(
            mapOf(
                "challengeToken" to "abc+def",
                "flow" to "buy",
                "clientToken" to "client",
                "theme" to "dark",
                "channelId" to "ch_7",
            ),
            url.toHttpUrl().parameters(),
        )
    }

    @Test(expected = MoonPayFrameException.InvalidUrl::class)
    fun `challenge with a broken url fails`() {
        MoonPayFrame.Challenge(url = "not a url", clientToken = "client", theme = null, resume = false)
            .url(MoonPayEnvironment.Production, channelId = "c")
    }

    @Test(expected = MoonPayFrameException.InvalidUrl::class)
    fun `a challenge outside MoonPay's origin is refused`() {
        MoonPayFrame.Challenge(url = "https://evil.example/challenge", clientToken = "client", theme = null, resume = false)
            .url(MoonPayEnvironment.Production, channelId = "c")
    }

    @Test
    fun `payment and connection checks run headless, the rest is shown`() {
        val headless = listOf(
            MoonPayFrame.CheckConnection(sessionToken = "s", publicKeyHex = "p"),
            MoonPayFrame.Buy(clientToken = "c", signature = "s"),
        )
        val modal = listOf(
            MoonPayFrame.Connect(clientToken = "c", publicKeyHex = "p", theme = null),
            MoonPayFrame.Widget(clientToken = "c", quoteSignature = "s", theme = null),
            MoonPayFrame.AddCard(clientToken = "c", theme = null),
            MoonPayFrame.Challenge(url = "https://platform.moonpay.com/c", clientToken = "c", theme = null, resume = false),
        )

        assertTrue(headless.all { it.presentation == MoonPayFrames.Presentation.Headless })
        assertTrue(modal.all { it.presentation == MoonPayFrames.Presentation.Modal })
    }

    @Test
    fun `sandbox loads frames from the same origin`() {
        val url = MoonPayFrame.AddCard(clientToken = "client", theme = null)
            .url(MoonPayEnvironment.Sandbox, channelId = "c")
            .toHttpUrl()

        assertEquals("platform.moonpay.com", url.host)
    }

    @Test
    fun `a frame never prints its tokens`() {
        val frame = MoonPayFrame.Connect(clientToken = "secret-client", publicKeyHex = "pub", theme = null)

        assertEquals("connect", frame.toString())
        assertFalse("$frame".contains("secret"))
    }

    @Test
    fun `only MoonPay's frame origin is trusted`() {
        val environment = MoonPayEnvironment.Production

        assertTrue(environment.isFrameOrigin("https://platform.moonpay.com"))
        assertTrue(environment.isFrameOrigin("https://platform.moonpay.com/"))
        assertTrue(environment.isFrameOrigin("HTTPS://Platform.MoonPay.com"))
        assertTrue(environment.isFrameOrigin("https://platform.moonpay.com:443"))
        assertFalse(environment.isFrameOrigin("http://platform.moonpay.com"))
        assertFalse(environment.isFrameOrigin("https://platform.moonpay.com:8443"))
        assertFalse(environment.isFrameOrigin("https://evil.example"))
        assertFalse(environment.isFrameOrigin("https://platform.moonpay.com.evil.example"))
        assertFalse(environment.isFrameOrigin("https://sub.platform.moonpay.com"))
        assertFalse(environment.isFrameOrigin(""))
    }

    private fun HttpUrl.parameters(): Map<String, String?> = queryParameterNames.associateWith { queryParameter(it) }
}
