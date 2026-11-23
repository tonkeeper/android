package com.tonapps.wallet.api.internal

import io.tonendpointapi.apis.ContentApi
import io.tonendpointapi.apis.StoriesApi
import okhttp3.Call
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

class WithQueryTest {

    private val context = linkedMapOf("lang" to "ru", "chainName" to "testnet", "wallet_id" to "wallet", "features" to "multichain")

    private fun send(call: (Call.Factory) -> Unit): Request {
        var sent: Request? = null
        val client = Call.Factory { request ->
            sent = request
            throw IOException("not sent")
        }
        assertThrows(IOException::class.java) { call(client.withQuery(context, "ru_RU")) }
        return sent!!
    }

    @Test
    fun replacesGeneratedDefaultsAndAddsUndeclaredParameters() {
        val request = send { ContentApi("https://api.example.com", it).getPopularApps(withAds = null) }

        assertEquals("/apps/popular", request.url.encodedPath)
        assertEquals(listOf("ru"), request.url.queryParameterValues("lang"))
        assertEquals("testnet", request.url.queryParameter("chainName"))
        assertEquals("multichain", request.url.queryParameter("features"))
        assertNull(request.url.queryParameter("with_ads"))
        assertEquals("ru_RU", request.header("Accept-Language"))
    }

    @Test
    fun keepsOperationParameters() {
        val request = send { StoriesApi("https://api.example.com", it).getStoriesBatch(ids = "staking,swaps", isNew = false) }

        assertEquals("staking,swaps", request.url.queryParameter("ids"))
        assertEquals("false", request.url.queryParameter("is_new"))
        assertEquals("wallet", request.url.queryParameter("wallet_id"))
    }
}
