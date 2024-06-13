package com.tonapps.wallet.data.account.source

import android.content.Context
import androidx.core.content.edit
import com.tonapps.blockchain.ton.extensions.hex
import com.tonapps.extensions.prefs
import org.ton.api.pub.PublicKeyEd25519

internal class StorageSource(context: Context) {

    private companion object {
        private const val NAME = "account"
        private const val SELECTED_ID = "selected_id"
        private const val TON_PROOF_TOKEN_PREFIX = "ton_proof_token"
    }

    private val prefs = context.prefs(NAME)

    fun getTonProofToken(id: String): String? {
        val value = prefs.getString(tonProofToken(id), null)
        if (value.isNullOrBlank()) {
            return null
        }
        return value
    }

    fun setTonProofToken(id: String, token: String) {
        prefs.edit {
            putString(tonProofToken(id), token)
        }
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

    private fun tonProofToken(id: String): String {
        return key(TON_PROOF_TOKEN_PREFIX, id)
    }

    private fun key(prefix: String, id: String): String {
        return "${prefix}_${id}"
    }
}