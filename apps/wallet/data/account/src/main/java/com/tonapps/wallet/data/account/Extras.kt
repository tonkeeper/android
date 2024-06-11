package com.tonapps.wallet.data.account

import android.content.Context
import com.tonapps.security.securePrefs
import com.tonapps.wallet.api.API

internal class Extras(
    private val context: Context,
    private val api: API
) {

    private val prefs = context.securePrefs("wallet_extras")

    fun getTonProofToken(walletId: String): String? {
        return prefs.getString(tonProofToken(walletId), null)
    }

    fun setTonProofToken(walletId: String, token: String) {
        prefs.edit().putString(tonProofToken(walletId), token).apply()
    }

    private companion object {

        private fun tonProofToken(walletId: String): String {
            return "ton_proof_token:$walletId"
        }
    }

}