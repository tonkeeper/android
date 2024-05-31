package com.tonapps.wallet.data.settings.folder

import android.content.Context
import com.tonapps.wallet.data.settings.entities.WalletPrefsEntity

internal class WalletPrefsFolder(context: Context): BaseSettingsFolder(context, "wallet_prefs") {

    private companion object {
        private const val SORT_PREFIX = "sort_"
    }

    fun get(walletId: Long): WalletPrefsEntity {
        val index = getInt(keySort(walletId))
        return WalletPrefsEntity(
            index = index
        )
    }

    fun setSort(walletIds: List<Long>) {
        edit {
            for ((index, walletId) in walletIds.withIndex()) {
                putInt(keySort(walletId), index)
            }
        }
    }

    private fun keySort(walletId: Long): String {
        return key(SORT_PREFIX, walletId)
    }

    private fun key(prefix: String, walletId: Long): String {
        return "$prefix$walletId"
    }
}