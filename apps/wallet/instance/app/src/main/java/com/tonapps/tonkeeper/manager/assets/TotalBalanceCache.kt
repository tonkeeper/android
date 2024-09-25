package com.tonapps.tonkeeper.manager.assets

import com.tonapps.icu.Coins
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.core.WalletCurrency
import java.util.concurrent.ConcurrentHashMap

internal class TotalBalanceCache {

    private val memoryCache = ConcurrentHashMap<String, Coins>(100, 1.0f, 2)

    fun get(
        wallet: WalletEntity,
        currency: WalletCurrency,
        sorted: Boolean
    ): Coins? {
        val key = createKey(wallet, currency, sorted)
        return memoryCache[key]?.copy()
    }

    fun set(
        wallet: WalletEntity,
        currency: WalletCurrency,
        sorted: Boolean,
        value: Coins
    ) {
        val key = createKey(wallet, currency, sorted)
        memoryCache[key] = value.copy()
    }

    private fun createKey(
        wallet: WalletEntity,
        currency: WalletCurrency,
        sorted: Boolean
    ): String {
        val prefix = "${wallet.id}_${currency.code}"
        if (sorted) {
            return "${prefix}_sorted"
        }
        return prefix
    }

    fun clear() {
        memoryCache.clear()
    }
}