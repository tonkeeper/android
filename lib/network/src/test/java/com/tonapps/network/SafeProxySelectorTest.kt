package com.tonapps.network

import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI

class SafeProxySelectorTest {

    @Test
    fun `drops proxies with hosts OkHttp can't canonicalize`() {
        listOf("internet ", "interest ", "googell.com net", "", " ", "%61bc").forEach { host ->
            assertEquals(host, listOf(Proxy.NO_PROXY), SafeProxySelector.withValidHosts(listOf(http(host))))
        }
    }

    @Test
    fun `keeps proxies with valid hosts`() {
        val proxies = listOf(http("proxy.example.com"), http("10.0.0.1"), http("::1"))

        assertEquals(proxies, SafeProxySelector.withValidHosts(proxies))
    }

    @Test
    fun `keeps the valid proxies of a mixed list`() {
        val valid = http("proxy.example.com")

        assertEquals(listOf(valid), SafeProxySelector.withValidHosts(listOf(http("internet "), valid)))
    }

    @Test
    fun `keeps a SOCKS proxy whatever its host`() {
        val socks = Proxy(Proxy.Type.SOCKS, InetSocketAddress.createUnresolved("internet ", 1080))

        assertEquals(listOf(socks), SafeProxySelector.withValidHosts(listOf(socks)))
    }

    @Test
    fun `keeps a direct connection`() {
        assertEquals(listOf(Proxy.NO_PROXY), SafeProxySelector.withValidHosts(listOf(Proxy.NO_PROXY)))
    }

    @Test
    fun `a call through an APN proxy with an invalid host fails with IOException, not IllegalArgumentException`() {
        val default = ProxySelector.getDefault()
        ProxySelector.setDefault(FixedProxySelector(http("internet ")))
        try {
            val client = OkHttpClient.Builder().safeProxySelector().build()
            val call = client.newCall(Request.Builder().url("http://127.0.0.1:1/").build())

            assertThrows(IOException::class.java) { call.execute() }
        } finally {
            ProxySelector.setDefault(default)
        }
    }

    private class FixedProxySelector(private val proxy: Proxy) : ProxySelector() {

        override fun select(uri: URI?): List<Proxy> = listOf(proxy)

        override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) = Unit
    }

    private fun http(host: String): Proxy {
        return Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved(host, 8080))
    }
}
