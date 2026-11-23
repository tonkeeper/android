package com.tonapps.perps.security

import android.content.Context
import androidx.core.content.edit
import com.tonapps.async.Async
import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.log.L
import com.tonapps.wallet.api.API
import kotlinx.coroutines.withContext
import java.util.Base64

interface PerpsSecurityConfigStore {
    fun keptBlock(): String?
    fun keep(block: String)
}

class PerpsSecurityConfigPrefsStore(context: Context) : PerpsSecurityConfigStore {

    private val prefs = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun keptBlock(): String? = prefs.getString(BLOCK_KEY, null)

    override fun keep(block: String) {
        prefs.edit { putString(BLOCK_KEY, block) }
    }

    private companion object {
        const val PREFERENCES_NAME = "perps_security"
        const val BLOCK_KEY = "verified_block"
    }
}

class PerpsSecurityConfigRepository(
    private val servedBlock: () -> String?,
    private val store: PerpsSecurityConfigStore,
    private val trustedKeys: Map<Int, ByteArray> = TRUSTED_KEYS,
    private val awaitDownloadedConfig: suspend () -> Unit = {},
) {

    private val lock = Any()

    private var lastRead: VerifiedRead? = null

    constructor(api: API, store: PerpsSecurityConfigStore) : this(
        servedBlock = { api.getConfig(TonNetwork.MAINNET).perpsSecurity },
        store = store,
        awaitDownloadedConfig = { api.awaitDownloadedConfig() },
    )

    suspend fun verified(isComplete: (PerpsSecurityConfig) -> Boolean = { true }): PerpsSecurityConfig? = withContext(Async.Io) {
        read()?.takeIf(isComplete) ?: run {
            awaitDownloadedConfig()
            read()
        }
    }

    suspend fun verifiedWithoutWaiting(): PerpsSecurityConfig? = withContext(Async.Io) { read() }

    private fun read(): PerpsSecurityConfig? {
        synchronized(lock) {
            val keptBlock = store.keptBlock()
            val served = servedBlock()
            lastRead?.takeIf { it.keptBlock == keptBlock && it.servedBlock == served }?.let { return it.config }
            val kept = keptBlock?.let { PerpsSecurityConfigReader.read(it, trustedKeys) }
            val fresh = served?.takeIf { it != keptBlock }?.let { PerpsSecurityConfigReader.read(it, trustedKeys) }
            val config = when {
                fresh == null -> kept
                kept != null && fresh.version < kept.version -> {
                    L.w("perps security config v${fresh.version} is older than the kept v${kept.version}")
                    kept
                }
                else -> {
                    store.keep(checkNotNull(served))
                    fresh
                }
            }
            lastRead = VerifiedRead(keptBlock = store.keptBlock(), servedBlock = served, config = config)
            return config
        }
    }

    private class VerifiedRead(val keptBlock: String?, val servedBlock: String?, val config: PerpsSecurityConfig?)

    private companion object {
        val TRUSTED_KEYS: Map<Int, ByteArray> = mapOf(
            1 to Base64.getDecoder().decode("TSVyO+TGwwp9YCd5J25nUplZ66onplW1fJqtovYyqPc="),
        )
    }
}
