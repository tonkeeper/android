package com.tonapps.tonkeeper.client.safemode

import android.content.Context
import android.net.Uri
import android.util.Log
import com.tonapps.extensions.MutableEffectFlow
import com.tonapps.icu.Coins
import com.tonapps.tonkeeper.client.safemode.BadDomainsEntity.Companion.isNullOrEmpty
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.core.BlobDataSource
import com.tonapps.wallet.data.settings.SafeModeState
import com.tonapps.wallet.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class SafeModeClient(
    private val context: Context,
    private val api: API,
    private val scope: CoroutineScope,
) {

    private val blobCache: BlobDataSource<BadDomainsEntity> by lazy {
        BlobDataSource.simple<BadDomainsEntity>(context, "safemode")
    }

    private val _scamDomainsFlow = MutableStateFlow<Array<String>?>(null)
    val scamDomainsFlow = _scamDomainsFlow.asStateFlow().filterNotNull()

    init {
        scope.launch(Dispatchers.IO) {
            applyCachedDomains()
            applyRemoteDomains()
        }
    }

    private fun applyCachedDomains() {
        val domains = getCachedBadDomains()
        if (!domains.isEmpty) {
            setDomains(domains)
        }
    }

    private suspend fun applyRemoteDomains() {
        val domains = loadBadDomains()
        if (!domains.isEmpty) {
            setDomains(domains)
        }
    }

    private fun setDomains(domains: BadDomainsEntity) {
        _scamDomainsFlow.value = domains.array
    }

    suspend fun isHasScamUris(vararg uris: Uri): Boolean {
        val scamDomains = scamDomainsFlow.first()
        val masks = scamDomains.filter { it.startsWith("*.") }.map { it.removePrefix("*.") }
        val telegramDomains = scamDomains.filter { it.startsWith("t.me/") }
        val exactDomains = scamDomains.filterNot { it.startsWith("*.") || it.startsWith("t.me/") }

        val fixedUris = uris.filter { it != Uri.EMPTY && it.scheme == "https" }
        for (uri in fixedUris) {
            var host = uri.host ?: continue
            if (host.startsWith("www.")) {
                host = host.substring(4)
            }
            if (host == "t.me") {
                val path = uri.path.orEmpty().removePrefix("/")
                if (path.isNotEmpty() && "t.me/$path" in telegramDomains) {
                    return true
                }
            }
            if (host in exactDomains) {
                return true
            }
            if (masks.any { mask -> host == mask || host.endsWith(".$mask") }) {
                return true
            }
        }
        return false
    }

    private fun getCachedBadDomains(): BadDomainsEntity {
        val entity = blobCache.getCache("scam_domains")
        if (entity == null || entity.isEmpty) {
            return BadDomainsEntity(emptyArray())
        }
        return entity
    }

    private suspend fun loadBadDomains(): BadDomainsEntity {
        val domains = api.getScamDomains()
        if (domains.isEmpty()) {
            return BadDomainsEntity(emptyArray())
        }
        val entity = BadDomainsEntity(domains)
        blobCache.setCache("scam_domains", entity)
        return entity
    }

}