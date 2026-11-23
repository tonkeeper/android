package com.tonapps.wallet.api.internal

import android.content.Context
import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.core.flags.BootFlags
import com.tonapps.extensions.toByteArray
import com.tonapps.extensions.toParcel
import com.tonapps.log.L
import com.tonapps.wallet.api.entity.ConfigEntity
import com.tonapps.wallet.api.entity.ConfigResponseEntity
import com.tonapps.wallet.api.entity.bootFlags
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.seconds

// The cache is a parcel of ConfigEntity: a field added to it (or to FlagsEntity) needs a new file name.
private const val CONFIG_FILE_NAME = "config_all_v8"
private val OLD_CONFIG_FILE_NAMES = listOf("config_all", "config_all_v2", "config_all_v3", "config_all_v4", "config_all_v5", "config_all_v6", "config_all_v7")
private val CONFIG_DOWNLOAD_RETRY_DELAY = 5.seconds

internal class ConfigRepository(
    private val context: Context,
    scope: CoroutineScope,
    private val internalApi: InternalApi,
) {

    private val configFileLock = Any()

    private val configDownload = SharedConfigDownload(scope, CONFIG_DOWNLOAD_RETRY_DELAY) { downloadConfig() }

    private val _stream = MutableStateFlow(ConfigEntity.default)

    val stream = _stream.asStateFlow()

    private val _overlaid = MutableStateFlow(ConfigEntity.default)

    /**
     * The mainnet config with the debug overlay on it. Set right after BootFlags on both paths, a new config and a
     * new overlay, so whoever hears of it reads its flags. A StateFlow: an overlay that changes nothing emits nothing.
     */
    val overlaid = _overlaid.asStateFlow()

    var configMainnetEntity: ConfigEntity = ConfigEntity.default
        private set(value) {
            field = value
            internalApi.setApiUrl(value.tonkeeperApiUrl)
        }

    var configTestnetEntity: ConfigEntity = ConfigEntity.default
        private set

    // Read and written only on Main, in setConfig: the cache and the download race, and the download wins.
    private var hasRemoteConfig = false

    init {
        scope.launch(Dispatchers.IO) {
            val cached = readCache()
            if (cached != null) {
                setConfig(cached, fromCache = true)
            } else {
                for (name in OLD_CONFIG_FILE_NAMES) {
                    File(context.filesDir, name).delete()
                    File(context.cacheDir, name).delete()
                }
                initConfig()
            }
        }

        // A debug overlay republishes the config it sits on, not the flags already overlaid. Its current value too:
        // RootActivity can set it before this collector subscribes, and publishing twice is harmless.
        scope.launch(Dispatchers.Main) {
            BootConfigOverrides.overlay.collect { publish() }
        }
    }

    private suspend fun setConfig(config: ConfigResponseEntity, fromCache: Boolean = false) = withContext(Dispatchers.Main) {
        // A cache read that finishes after a download would put the older config back.
        if (fromCache && hasRemoteConfig) {
            return@withContext
        }
        hasRemoteConfig = hasRemoteConfig || !fromCache

        configMainnetEntity = config.mainnet
        configTestnetEntity = config.testnet

        // Published before the streams emit, so whoever hears of the config reads its flags.
        publish()

        _stream.value = configMainnetEntity
    }

    // The only writer of BootFlags and of [overlaid], on Main, in that order: one snapshot of the stored config with
    // the debug overlay on it.
    private fun publish() {
        val config = getConfig(TonNetwork.MAINNET)
        BootFlags.update(config.flags.bootFlags)
        _overlaid.value = config
    }

    private fun getConfigFile(): File {
        return synchronized(configFileLock) {
            val file = File(context.filesDir, CONFIG_FILE_NAME)

            val legacy = File(context.cacheDir, CONFIG_FILE_NAME)
            if (legacy.exists() && !legacy.renameTo(file)) {
                return@synchronized legacy
            }

            file
        }
    }

    private fun readCache(): ConfigResponseEntity? {
        val file = getConfigFile()
        if (file.length() > 0) {
            return file.readBytes().toParcel()
        }
        return null
    }

    private suspend fun remote(): ConfigResponseEntity? = withContext(Dispatchers.IO) {
        internalApi.downloadConfig()
    }

    suspend fun refresh() {
        configDownload.refresh()
    }

    suspend fun awaitDownloadedConfig(): Boolean {
        return try {
            configDownload.awaitDownloaded()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            L.e(e)
            false
        }
    }

    private suspend fun downloadConfig(): Boolean {
        val config = remote() ?: return false
        withContext(Dispatchers.IO) {
            getConfigFile().writeBytes(config.toByteArray())
        }
        setConfig(config)
        return true
    }

    suspend fun initConfig() = refresh()

    fun getConfig(network: TonNetwork): ConfigEntity {
        val base = when (network) {
            TonNetwork.MAINNET -> configMainnetEntity
            TonNetwork.TESTNET -> configTestnetEntity
        }
        return BootConfigOverrides.applyTo(base)
    }

}