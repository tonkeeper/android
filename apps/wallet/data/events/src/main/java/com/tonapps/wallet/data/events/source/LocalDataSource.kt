package com.tonapps.wallet.data.events.source

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.tonapps.extensions.MutableEffectFlow
import com.tonapps.security.Security
import com.tonapps.wallet.api.fromJSON
import com.tonapps.wallet.api.toJSON
import com.tonapps.wallet.data.core.BlobDataSource
import io.tonapi.models.AccountEvents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn

internal class LocalDataSource(
    scope: CoroutineScope,
    context: Context
): BlobDataSource<AccountEvents>(
    context = context,
    path = "events"
) {

    companion object {
        private const val NAME = "events"
        private const val KEY_ALIAS = "_com_tonapps_events_master_key_"
    }

    private val _decryptedCommentFlow = MutableEffectFlow<Unit>()
    val decryptedCommentFlow = _decryptedCommentFlow.shareIn(scope, SharingStarted.WhileSubscribed(), 1)

    private val encryptedPrefs = Security.pref(context, KEY_ALIAS, NAME)

    private fun keyDecryptedComment(txId: String): String {
        return "tx_$txId"
    }

    fun getDecryptedComment(txId: String): String? {
        return encryptedPrefs.getString(keyDecryptedComment(txId), null)
    }

    fun saveDecryptedComment(txId: String, comment: String) {
        encryptedPrefs.edit {
            putString(keyDecryptedComment(txId), comment)
            _decryptedCommentFlow.tryEmit(Unit)
        }
    }

    override fun onMarshall(data: AccountEvents): ByteArray {
        val json = toJSON(data)
        return json.toByteArray()
    }

    override fun onUnmarshall(bytes: ByteArray): AccountEvents? {
        if (bytes.isEmpty()) {
            return null
        }
        return try {
            val string = String(bytes)
            fromJSON(string)
        } catch (e: Throwable) {
            null
        }
    }
}