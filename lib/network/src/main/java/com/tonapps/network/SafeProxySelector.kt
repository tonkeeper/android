package com.tonapps.network

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import okhttp3.Dns
import okhttp3.OkHttpClient
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import android.net.Proxy as AndroidProxy

class SafeProxySelector private constructor(
    private val systemSelector: ProxySelector,
) : ProxySelector() {

    override fun select(uri: URI?): List<Proxy> {
        val proxies = try {
            systemSelector.select(uri)
        } catch (e: IllegalArgumentException) {
            return listOf(Proxy.NO_PROXY)
        }
        return withValidHosts(proxies)
    }

    override fun connectFailed(uri: URI?, address: SocketAddress?, failure: IOException?) {
        systemSelector.connectFailed(uri, address, failure)
    }

    private object ProxyChangeReceiver : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            installAsDefault()
        }
    }

    companion object {

        // OkHttp 5.5+ throws IllegalArgumentException, not IOException, for an HTTP proxy host it
        // can't canonicalize (an APN proxy typed as "internet "), and that crashes the dispatcher
        // thread. OkHttp never resolves a SOCKS proxy host, so SOCKS proxies are kept.
        internal fun withValidHosts(proxies: List<Proxy>): List<Proxy> {
            return proxies.filter(::hasValidHost).ifEmpty { listOf(Proxy.NO_PROXY) }
        }

        private fun hasValidHost(proxy: Proxy): Boolean {
            if (proxy.type() != Proxy.Type.HTTP) {
                return true
            }
            val address = proxy.address() as? InetSocketAddress ?: return true
            if (address.address != null) {
                return true
            }
            return runCatching { Dns.Request(address.hostString) }.isSuccess
        }

        fun install(context: Context) {
            installAsDefault()
            ContextCompat.registerReceiver(
                context,
                ProxyChangeReceiver,
                IntentFilter(AndroidProxy.PROXY_CHANGE_ACTION),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }

        fun fromDefault(): ProxySelector? {
            val systemSelector = getDefault() ?: return null
            return systemSelector as? SafeProxySelector ?: SafeProxySelector(systemSelector)
        }

        private fun installAsDefault() {
            fromDefault()?.let { setDefault(it) }
        }
    }
}

fun OkHttpClient.Builder.safeProxySelector(): OkHttpClient.Builder {
    val selector = SafeProxySelector.fromDefault() ?: return this
    return proxySelector(selector)
}
