package com.tonapps.tonkeeper.client.safemode

import android.content.Context
import android.net.Uri
import android.util.Log
import com.tonapps.icu.Coins
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.core.BlobDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class SafeModeClient(
    private val context: Context,
    private val api: API,
    private val scope: CoroutineScope
) {

    private val scamDomains = ConcurrentHashMap<String, Boolean>(3, 1.0f, 2)
    private val blobCache = BlobDataSource.simple<BadDomainsEntity>(context, "safemode")
    private val badDomainsFlow = flow {
        getCachedBadDomains()?.let {
            emit(it)
        }

        loadBadDomains()?.let {
            emit(it)
        }
    }.distinctUntilChanged()

    init {
        badDomainsFlow.onEach {
            for (domain in it.array) {
                scamDomains[domain] = true
            }
        }.launchIn(scope)
    }

    fun isHasScamUris(vararg uris: Uri): Boolean {
        for (uri in uris) {
            if (uri == Uri.EMPTY) {
                continue
            }
            var host = uri.host ?: continue
            if (host.startsWith("www.")) {
                host = host.substring(4)
            }
            if (scamDomains.containsKey(host)) {
                return true
            }
        }
        return false
    }

    private fun getCachedBadDomains(): BadDomainsEntity? {
        val entity = blobCache.getCache("scam_domains")
        if (entity == null || entity.isEmpty) {
            return null
        }
        return entity
    }

    private suspend fun loadBadDomains(): BadDomainsEntity? {
        val domains = api.getScamDomains()
        if (domains.isEmpty()) {
            return null
        }
        val entity = BadDomainsEntity(domains)
        blobCache.setCache("scam_domains", entity)
        return entity
    }

}