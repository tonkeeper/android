package com.tonapps.tonkeeper.manager.earn

import android.net.Uri
import androidx.core.net.toUri
import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.tonkeeper.extensions.isSameOriginOrSubdomain
import com.tonapps.tonkeeper.extensions.siteOriginRules
import com.tonapps.wallet.api.API
import org.json.JSONArray

class EarnUrlResolver(private val api: API) {

    // Same fallback as iOS (EarnHardcodedData.url) until the backend serves earn_url.
    val base: Uri
        get() = api.getConfig(TonNetwork.MAINNET)
            .earnUrl
            .takeIf { it.isNotBlank() }
            ?.toUri()
            ?.takeIf { it.scheme == "https" && !it.host.isNullOrBlank() }
            ?: FALLBACK_URL.toUri()

    val featuredVaults: JSONArray
        get() = runCatching {
            JSONArray(api.getConfig(TonNetwork.MAINNET).earnFeaturedVaults)
        }.getOrDefault(JSONArray())

    fun vaultUrl(network: String, vaultId: String): Uri = base
        .buildUpon()
        .appendPath("earn")
        .appendPath(network)
        .appendPath(vaultId)
        .build()

    fun isEarn(url: Uri): Boolean = url.isSameOriginOrSubdomain(base)

    fun originRules(startUrl: Uri): Set<String> {
        // Whether we may inject at all is decided by the page we were opened with.
        if (!isEarn(startUrl)) {
            return emptySet()
        }
        // What the rules cover is the whole Earn site: opening a subdomain must not narrow them
        // to that subdomain.
        val source = base
        return siteOriginRules(source.scheme, source.host, source.port)
    }

    private companion object {
        const val FALLBACK_URL = "https://vaults-tonkeeper-earn-demo.fly.dev"
    }
}