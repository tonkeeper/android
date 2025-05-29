package com.tonapps.wallet.data.settings.folder

import android.content.Context
import android.util.Log
import com.tonapps.wallet.data.settings.entities.TokenPrefsEntity
import kotlinx.coroutines.CoroutineScope

internal class TokenPrefsFolder(context: Context, scope: CoroutineScope): BaseSettingsFolder(context, scope, "token_prefs") {

    private companion object {
        private const val PINNED_PREFIX = "pinned_"
        private const val STATE_PREFIX = "state_"
        private const val SORT_PREFIX = "sort_"
        private const val HIDDEN_PREFIX = "hidden_"
    }

    fun get(walletId: String, tokenAddress: String, blacklist: Boolean): TokenPrefsEntity {
        if (blacklist && !contains(keyState(walletId, tokenAddress))) {
            return TokenPrefsEntity(
                pinned = false,
                state = TokenPrefsEntity.State.SPAM,
                hidden = true,
            )
        }

        val state = getState(walletId, tokenAddress)
        val hidden = getHidden(walletId, tokenAddress)
        val pinned = getPinned(walletId, tokenAddress)
        val index = getIndex(walletId, tokenAddress)
        return TokenPrefsEntity(
            pinned = pinned,
            state = state,
            hidden = hidden,
            index = index,
        )
    }

    fun getState(walletId: String, tokenAddress: String): TokenPrefsEntity.State {
        val value = getInt(keyState(walletId, tokenAddress))
        return TokenPrefsEntity.state(value)
    }

    fun getHidden(walletId: String, tokenAddress: String): Boolean {
        // trc20 usdt
        val defValue = tokenAddress == "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t"
        return getBoolean(keyHidden(walletId, tokenAddress), defValue)
    }

    fun getPinned(walletId: String, tokenAddress: String): Boolean {
        val defValue = tokenAddress.equals("0:b113a994b5024a16719f69139328eb759596c38a25f59028b146fecdc3621dfe", ignoreCase = true) || tokenAddress.equals("ton", ignoreCase = true)
        return getBoolean(keyPinned(walletId, tokenAddress), defValue)
    }

    fun getIndex(walletId: String, tokenAddress: String): Int {
        return getInt(keySort(walletId, tokenAddress))
    }

    fun setPinned(walletId: String, tokenAddress: String, pinned: Boolean) {
        putBoolean(keyPinned(walletId, tokenAddress), pinned)
    }

    fun setHidden(walletId: String, tokenAddress: String, hidden: Boolean) {
        putBoolean(keyHidden(walletId, tokenAddress), hidden)
    }

    fun setState(walletId: String, tokenAddress: String, state: TokenPrefsEntity.State) {
        putInt(keyState(walletId, tokenAddress), state.state)
    }

    fun setSort(walletId: String, tokensAddress: List<String>) {
        edit {
            for ((index, tokenAddress) in tokensAddress.withIndex()) {
                putInt(keySort(walletId, tokenAddress), index)
            }
        }
    }

    private fun keyState(walletId: String, tokenAddress: String): String {
        return key(STATE_PREFIX, walletId, tokenAddress)
    }

    private fun keyHidden(walletId: String, tokenAddress: String): String {
        return key(HIDDEN_PREFIX, walletId, tokenAddress)
    }

    private fun keyPinned(walletId: String, tokenAddress: String): String {
        return key(PINNED_PREFIX, walletId, tokenAddress)
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