package com.tonapps.wallet.data.account.source

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.tonapps.blockchain.ton.extensions.hex
import com.tonapps.extensions.prefs
import com.tonapps.extensions.putStringIfNotExists
import com.tonapps.extensions.string
import com.tonapps.security.Security
import org.ton.api.pub.PublicKeyEd25519

internal class StorageSource(context: Context) {

    private companion object {
        private const val NAME = "account"
        private const val SELECTED_ID = "selected_id"
        private const val TON_PROOF_TOKEN_PREFIX = "ton_proof_token"
        private const val KEY_ALIAS = "_com_tonapps_account_master_key_"
    }

    private val prefs = context.prefs(NAME)
    private val encryptedPrefs: SharedPreferences by lazy { Security.pref(context, KEY_ALIAS, NAME) }

    fun getTonProofToken(publicKey: PublicKeyEd25519): String? {
        val key = tonProofTokenKey(publicKey)
        return encryptedPrefs.string(key) ?: prefs.string(key)
    }

    fun setTonProofToken(publicKey: PublicKeyEd25519, token: String) {
        val key = tonProofTokenKey(publicKey)
        encryptedPrefs.putStringIfNotExists(key, token)
    }

    fun getSelectedId(): String? {
        val id = prefs.getString(SELECTED_ID, null) ?: return null
        if (id.isBlank()) {
            return null
        }
        return id
    }

    fun setSelectedId(id: String?) {
        prefs.edit {
            if (id.isNullOrBlank()) {
                remove(SELECTED_ID)
            } else {
                putString(SELECTED_ID, id)
            }
        }
    }

    private fun tonProofTokenKey(publicKey: PublicKeyEd25519): String {
        return key(TON_PROOF_TOKEN_PREFIX, publicKey.hex())
    }

    private fun key(prefix: String, id: String): String {
        return "${prefix}_${id}"
    }

    fun clear() {
        prefs.edit {
            clear()
        }
    }
}