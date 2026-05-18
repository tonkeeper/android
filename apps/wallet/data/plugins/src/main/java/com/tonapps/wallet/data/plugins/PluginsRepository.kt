package com.tonapps.wallet.data.plugins

import android.content.Context
import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.core.BlobDataSource
import io.Serializer
import io.tonapi.models.WalletPlugin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class PluginsRepository(
    private val context: Context,
    private val api: API,
) : BlobDataSource<List<WalletPlugin>>(
    context = context,
    path = "plugins",
    timeout = TimeUnit.DAYS.toMillis(1)
) {

    private val _updatedFlow = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val updatedFlow = _updatedFlow.asSharedFlow()

    init {
        _updatedFlow.tryEmit(Unit)
    }

    suspend fun getPlugins(
        accountId: String,
        network: TonNetwork,
        refresh: Boolean = false,
    ): List<WalletPlugin> = withContext(Dispatchers.IO) {
        val key = cacheKey(accountId, network)
        if (!refresh) {
            val cached = getCache(key)
            if (cached != null) {
                return@withContext cached
            }
        }
        try {
            val wallet = api.wallet(network).getWalletInfo(accountId)
            val plugins = wallet.plugins
            setCache(key, plugins)
            _updatedFlow.emit(Unit)
            plugins
        } catch (_: Throwable) {
            val cached = getCache(key)
            if (cached != null) {
                return@withContext cached
            }
            emptyList()
        }
    }

    private fun cacheKey(accountId: String, network: TonNetwork): String {
        return "${accountId}_${network.name.lowercase()}"
    }

    override fun onMarshall(data: List<WalletPlugin>) = Serializer.toJSON(data).toByteArray()

    override fun onUnmarshall(bytes: ByteArray): List<WalletPlugin>? {
        if (bytes.isEmpty()) return null
        return try {
            val string = String(bytes)
            Serializer.fromJSON<List<WalletPlugin>>(string)
        } catch (e: Throwable) {
            null
        }
    }
}


