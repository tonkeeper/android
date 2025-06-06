package com.tonapps.tonkeeper.manager.assets

import android.content.Context
import androidx.core.content.edit
import com.tonapps.extensions.getParcelable
import com.tonapps.extensions.prefs
import com.tonapps.extensions.putParcelable
import com.tonapps.icu.Coins
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.core.currency.WalletCurrency

internal class TotalBalanceCache(context: Context) {

    private val storageCache = context.prefs("total_balance_cache")

    fun get(
        wallet: WalletEntity,
        currency: WalletCurrency,
        sorted: Boolean
    ): Coins? {
        val key = createKey(wallet, currency, sorted)
        return storageCache.getParcelable<Coins>(key)
    }

    fun set(
        wallet: WalletEntity,
        currency: WalletCurrency,
        sorted: Boolean,
        value: Coins
    ) {
        val key = createKey(wallet, currency, sorted)
        storageCache.putParcelable(key, value)
    }

    private fun createKey(
        wallet: WalletEntity,
        currency: WalletCurrency,
        sorted: Boolean
    ): String {
        val builder = StringBuilder(wallet.accountId)
        builder.append("_")
        builder.append(currency.code)
        if (wallet.testnet) {
            builder.append("_testnet")
        }
        if (sorted) {
            builder.append("_sorted")
        }
        return builder.toString()
    }

    fun clear(wallet: WalletEntity, currency: WalletCurrency) {
        storageCache.edit {
            remove(createKey(wallet, currency, true))
            remove(createKey(wallet, currency, false))
        }
    }

    fun clearAll() {
        storageCache.edit().clear().apply()
    }
}