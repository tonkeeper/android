package com.tonapps.wallet.api.internal

import android.content.Context
import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.extensions.toByteArray
import com.tonapps.extensions.toParcel
import com.tonapps.wallet.api.entity.ConfigEntity
import com.tonapps.wallet.api.entity.ConfigResponseEntity
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val CONFIG_FILE_NAME = "config_all"

internal class ConfigRepository(
    private val context: Context,
    scope: CoroutineScope,
    private val internalApi: InternalApi,
) {

    private val configFileLock = Any()

    private val _stream = MutableStateFlow(ConfigEntity.default)

    val stream = _stream.asStateFlow()

    var configMainnetEntity: ConfigEntity = ConfigEntity.default
        private set(value) {
            field = value
            internalApi.setApiUrl(value.tonkeeperApiUrl)
        }

    var configTestnetEntity: ConfigEntity = ConfigEntity.default
        private set

    var configTetraEntity: ConfigEntity = ConfigEntity.default
        private set

    init {
        scope.launch(Dispatchers.IO) {
            val cached = readCache()
            if (cached != null) {
                setConfig(cached)
            } else {
                initConfig()
            }
        }
    }

    private suspend fun setConfig(config: ConfigResponseEntity) = withContext(Dispatchers.Main) {
        configMainnetEntity = config.mainnet
        configTestnetEntity = config.testnet
        configTetraEntity = config.tetra
        _stream.value = configMainnetEntity
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
        val config = remote() ?: return
        withContext(Dispatchers.IO) {
            getConfigFile().writeBytes(config.toByteArray())
        }
        setConfig(config)
    }

    suspend fun initConfig() = refresh()

    fun getConfig(network: TonNetwork): ConfigEntity {
        val base = when (network) {
            TonNetwork.MAINNET -> configMainnetEntity
            TonNetwork.TESTNET -> configTestnetEntity
            TonNetwork.TETRA -> configTetraEntity
        }
        return BootConfigOverrides.applyTo(base)
    }

}
