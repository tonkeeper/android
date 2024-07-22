package com.tonapps.wallet.data.settings.folder

import android.content.Context
import android.util.Log
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.settings.entities.WalletPrefsEntity

internal class WalletPrefsFolder(context: Context): BaseSettingsFolder(context, "wallet_prefs") {

    private companion object {
        private const val SORT_PREFIX = "sort_"
        private const val PUSH_PREFIX = "push_"
        private const val PURCHASE_PREFIX = "purchase_"
        private const val SETUP_HIDDEN_PREFIX = "setup_hidden_"
    }

    fun isSetupHidden(walletId: String): Boolean {
        return getBoolean(key(SETUP_HIDDEN_PREFIX, walletId), false)
    }

    fun setupHide(walletId: String) {
        putBoolean(key(SETUP_HIDDEN_PREFIX, walletId), true)
    }

    fun isPurchaseOpenConfirm(walletId: String, id: String): Boolean {
        val key = keyPurchaseOpenConfirm(walletId, id)
        return getInt(key, 0) == 0
    }

    fun disablePurchaseOpenConfirm(walletId: String, id: String) {
        putInt(keyPurchaseOpenConfirm(walletId, id), 1)
    }

    fun isPushEnabled(walletId: String): Boolean {
        return getBoolean(keyPush(walletId), true)
    }

    fun setPushEnabled(walletId: String, enabled: Boolean) {
        putBoolean(keyPush(walletId), enabled)
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

    private fun keyPurchaseOpenConfirm(walletId: String, id: String): String {
        val key = key(PURCHASE_PREFIX, walletId)
        return "$key$id"
    }

    private fun keySort(walletId: String): String {
        return key(SORT_PREFIX, walletId)
    }

    private fun keyPush(walletId: String): String {
        return key(PUSH_PREFIX, walletId)
    }

    private fun key(prefix: String, walletId: String): String {
        return "$prefix$walletId"
    }
}