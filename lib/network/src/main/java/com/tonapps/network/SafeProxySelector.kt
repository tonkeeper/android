package com.tonapps.network

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import okhttp3.OkHttpClient
import java.io.IOException
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import android.net.Proxy as AndroidProxy

class SafeProxySelector private constructor(
    private val systemSelector: ProxySelector,
) : ProxySelector() {

    override fun select(uri: URI?): List<Proxy> {
        return try {
            systemSelector.select(uri)
        } catch (e: IllegalArgumentException) {
            listOf(Proxy.NO_PROXY)
        }
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
