package com.tonapps.tonkeeper.manager

import androidx.collection.ArrayMap
import com.tonapps.wallet.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.util.concurrent.ConcurrentHashMap

class BalancesManager(
    private val scope: CoroutineScope,
    private val settingsRepository: SettingsRepository,
) {

    private val memoryCache = ConcurrentHashMap<String, CharSequence>(100, 1.0f, 2)

    init {
        settingsRepository.currencyFlow.onEach {
            clear()
        }.launchIn(scope)
        settingsRepository.walletPrefsChangedFlow.onEach {
            clear()
        }.launchIn(scope)
    }

    fun clear() {
        memoryCache.clear()
    }

    operator fun set(walletId: String, balance: CharSequence) {
        memoryCache[walletId] = balance
    }

    operator fun get(walletId: String): CharSequence? {
        return memoryCache[walletId]
    }

    fun set(map: ArrayMap<String, CharSequence>) {
        for (entry in map) {
            memoryCache[entry.key] = entry.value
        }
    }

    fun get(walletIds: List<String>): ArrayMap<String, CharSequence> {
        val result = ArrayMap<String, CharSequence>()
        for (walletId in walletIds) {
            memoryCache[walletId]?.let { balance ->
                result[walletId] = balance
            }
        }
        return result
    }
}