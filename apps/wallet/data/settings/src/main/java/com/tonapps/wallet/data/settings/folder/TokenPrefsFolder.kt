package com.tonapps.wallet.data.settings.folder

import android.content.Context
import com.tonapps.wallet.data.settings.entities.TokenPrefsEntity

internal class TokenPrefsFolder(context: Context): BaseSettingsFolder(context, "token_prefs") {

    private companion object {
        private const val PINNED_PREFIX = "pinned_"
        private const val HIDDEN_PREFIX = "hidden_"
        private const val SORT_PREFIX = "sort_"
    }

    fun get(walletId: String, tokenAddress: String): TokenPrefsEntity {
        val hidden = getBoolean(keyHidden(walletId, tokenAddress))
        val pinned = if (hidden) false else getBoolean(keyPinned(walletId, tokenAddress), tokenAddress.equals("0:b113a994b5024a16719f69139328eb759596c38a25f59028b146fecdc3621dfe", ignoreCase = true) || tokenAddress.equals("ton", ignoreCase = true))
        val index = if (pinned) getInt(keySort(walletId, tokenAddress)) else -1
        return TokenPrefsEntity(
            pinned = pinned,
            hidden = hidden,
            index = index
        )
    }

    fun setPinned(walletId: String, tokenAddress: String, pinned: Boolean) {
        putBoolean(keyPinned(walletId, tokenAddress), pinned)
    }

    fun setHidden(walletId: String, tokenAddress: String, hidden: Boolean) {
        putBoolean(keyHidden(walletId, tokenAddress), hidden)
    }

    fun setSort(walletId: String, tokensAddress: List<String>) {
        edit {
            for ((index, tokenAddress) in tokensAddress.withIndex()) {
                putInt(keySort(walletId, tokenAddress), index)
            }
        }
    }

    private fun keyPinned(walletId: String, tokenAddress: String): String {
        return key(PINNED_PREFIX, walletId, tokenAddress)
    }

    private fun keyHidden(walletId: String, tokenAddress: String): String {
        return key(HIDDEN_PREFIX, walletId, tokenAddress)
    }

    private fun keySort(walletId: String, tokenAddress: String): String {
        return key(SORT_PREFIX, walletId, tokenAddress)
    }

    private fun key(
        prefix: String,
        walletId: String,
        tokenAddress: String
    ): String {
        return "$prefix$walletId:$tokenAddress"
    }
}