package com.tonapps.wallet.api.internal

import android.content.Context
import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.extensions.file
import com.tonapps.extensions.toByteArray
import com.tonapps.extensions.toParcel
import com.tonapps.wallet.api.entity.ConfigEntity
import com.tonapps.wallet.api.entity.ConfigResponseEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class ConfigRepository(
    context: Context,
    scope: CoroutineScope,
    private val internalApi: InternalApi,
) {

    private val configFile = context.cacheDir.file("config_all")
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

    private fun readCache(): ConfigResponseEntity? {
        if (configFile.exists() && configFile.length() > 0) {
            return configFile.readBytes().toParcel()
        }
        return null
    }

    private suspend fun remote(): ConfigResponseEntity? = withContext(Dispatchers.IO) {
        val response = internalApi.downloadConfig() ?: return@withContext null
        configFile.writeBytes(response.toByteArray())
        response
    }

    suspend fun refresh() {
        val config = remote() ?: return
        setConfig(config)
    }

    suspend fun initConfig() {
        remote()?.let {
            setConfig(it)
        }
    }

    fun getConfig(network: TonNetwork): ConfigEntity {
        return when (network) {
            TonNetwork.MAINNET -> configMainnetEntity
            TonNetwork.TESTNET -> configTestnetEntity
            TonNetwork.TETRA -> configTetraEntity
        }
    }

}