package com.tonapps.wallet.data.plugins

import android.content.Context
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.blockchain.ton.extensions.toRawAddress
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.core.BlobDataSource
import io.Serializer
import io.tonapi.models.Wallet
import io.tonapi.models.WalletPlugin
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.withTimeoutOrNull

class PluginsRepository(
    private val context: Context,
    private val api: API,
) : BlobDataSource<List<WalletPlugin>>(
    context = context,
    path = "plugins",
    timeout = TimeUnit.DAYS.toMillis(1)
) {

    private val signatureDisabledCache = simpleJSON<Boolean>(
        context = context,
        path = "signature_disabled",
        timeout = TimeUnit.DAYS.toMillis(1),
    )

    private val _updatedFlow = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val updatedFlow = _updatedFlow.asSharedFlow()

    init {
        _updatedFlow.tryEmit(Unit)
    }

    // WalletTg has no plugins, and tonapi does not serve wallet info for it.
    suspend fun getPlugins(
        wallet: WalletEntity,
        refresh: Boolean = false,
    ): List<WalletPlugin> {
        if (wallet.isWalletTg) {
            return emptyList()
        }
        return getPlugins(wallet.accountId, wallet.network, refresh)
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
            fetchWalletInfo(accountId, network).plugins
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            getCache(key) ?: emptyList()
        }
    }

    suspend fun isSignatureDisabled(wallet: WalletEntity): Boolean {
        if (wallet.isWalletTg) {
            return false
        }
        return isSignatureDisabled(wallet.accountId, wallet.network)
    }

    // A wallet whose 2FA extension turned off signing by key rejects every transaction Keeper signs.
    // Blocks only on a fresh answer, so a wallet the user has since unlocked in Keeper Pro is never held back.
    suspend fun isSignatureDisabled(
        accountId: String,
        network: TonNetwork,
    ): Boolean = withContext(Dispatchers.IO) {
        resolveSignatureDisabled(
            cached = signatureDisabledCache.getCache(cacheKey(accountId, network)),
            fetch = { fetchSignatureDisabled(accountId, network) },
        )
    }

    private suspend fun fetchSignatureDisabled(
        accountId: String,
        network: TonNetwork,
    ): Boolean? = withTimeoutOrNull(FRESH_TIMEOUT_MS) {
        try {
            fetchWalletInfo(accountId, network).signatureDisabled == true
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            null
        }
    }

    private suspend fun fetchWalletInfo(accountId: String, network: TonNetwork): Wallet {
        val wallet = runInterruptible(Dispatchers.IO) {
            api.wallet(network).getWalletInfo(accountId)
        }
        val key = cacheKey(accountId, network)
        setCache(key, wallet.plugins)
        signatureDisabledCache.setCache(key, wallet.signatureDisabled == true)
        _updatedFlow.emit(Unit)
        return wallet
    }

    private fun cacheKey(accountId: String, network: TonNetwork): String {
        return "${accountId.toRawAddress().lowercase()}_${network.name.lowercase()}"
    }

    override fun onMarshall(data: List<WalletPlugin>) = Serializer.toJSON(data).toByteArray()

    override fun onUnmarshall(bytes: ByteArray): List<WalletPlugin>? {
        if (bytes.isEmpty()) {
            return null
        }
        return try {
            val string = String(bytes)
            Serializer.fromJSON<List<WalletPlugin>>(string)
        } catch (e: Throwable) {
            null
        }
    }

    private companion object {
        const val FRESH_TIMEOUT_MS = 5_000L
    }
}

// A cached "no" signs right away; anything else asks tonapi, and an answer that does not come signs as before.
internal suspend fun resolveSignatureDisabled(
    cached: Boolean?,
    fetch: suspend () -> Boolean?,
): Boolean {
    if (cached == false) {
        return false
    }
    return fetch() ?: false
}
