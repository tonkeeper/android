package com.tonapps.wallet.data.settings.folder

import android.content.Context
import com.tonapps.wallet.data.settings.entities.WalletPrefsEntity

internal class WalletPrefsFolder(context: Context): BaseSettingsFolder(context, "wallet_prefs") {

    private companion object {
        private const val SORT_PREFIX = "sort_"
    }

    fun get(walletId: String): WalletPrefsEntity {
        val index = getInt(keySort(walletId))
        return WalletPrefsEntity(
            index = index
        )
    }

    fun setSort(walletIds: List<String>) {
        edit {
            for ((index, walletId) in walletIds.withIndex()) {
                putInt(keySort(walletId), index)
            }
        }
    }

    private fun keySort(walletId: String): String {
        return key(SORT_PREFIX, walletId)
    }

    private fun key(prefix: String, walletId: String): String {
        return "$prefix$walletId"
    }
}