package com.tonapps.wallet.data.multichain.lighter

import android.content.Context
import com.tonapps.wallet.data.multichain.wallet.CredentialIds
import androidx.core.content.edit

/**
 * The venue account each wallet's L2 key is registered against. Not a secret — the venue answers it
 * for the wallet's L1 address — so it lives in plain prefs, readable while the session key is locked.
 */
internal class LighterAccountPrefs(context: Context) {

    private val prefs = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun load(walletId: String): StoredLighterAccount? {
        val prefix = prefix(walletId)
        if (!prefs.contains(prefix + ACCOUNT_INDEX)) {
            return null
        }
        return StoredLighterAccount(
            accountIndex = prefs.getLong(prefix + ACCOUNT_INDEX, 0L),
            apiKeyIndex = prefs.getInt(prefix + API_KEY_INDEX, 0),
        )
    }

    // Committed, not applied: the activation is on disk before anything is signed with it, and a
    // failed write fails the activation instead of being lost with the process.
    fun save(walletId: String, account: StoredLighterAccount) {
        val prefix = prefix(walletId)
        val saved = prefs.edit()
            .putLong(prefix + ACCOUNT_INDEX, account.accountIndex)
            .putInt(prefix + API_KEY_INDEX, account.apiKeyIndex)
            .commit()
        check(saved) { "lighter: account of $walletId was not saved" }
    }

    fun remove(walletId: String) {
        val prefix = prefix(walletId)
        prefs.edit {
            remove(prefix + ACCOUNT_INDEX)
            remove(prefix + API_KEY_INDEX)
        }
    }

    private fun prefix(walletId: String) = "$walletId:${CredentialIds.LIGHTER_MAINNET}:"

    private companion object {
        const val NAME = "lighter_accounts"
        const val ACCOUNT_INDEX = "account_index"
        const val API_KEY_INDEX = "api_key_index"
    }
}
