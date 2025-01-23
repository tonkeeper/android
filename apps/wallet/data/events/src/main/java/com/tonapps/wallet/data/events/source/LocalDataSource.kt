package com.tonapps.wallet.data.events.source

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.tonapps.extensions.MutableEffectFlow
import com.tonapps.security.Security
import com.tonapps.wallet.data.core.BlobDataSource
import io.tonapi.models.AccountAddress
import io.tonapi.models.AccountEvent
import io.tonapi.models.AccountEvents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.shareIn

internal class LocalDataSource(
    scope: CoroutineScope,
    context: Context
) {

    companion object {
        private const val NAME = "events"
        private const val LATEST_RECIPIENTS = "latest_recipients"
        private const val KEY_ALIAS = "_com_tonapps_events_master_key_"
    }

    private val databaseSource: DatabaseSource = DatabaseSource(scope, context)
    private val eventsCache = BlobDataSource.simpleJSON<AccountEvents>(context, "events")
    private val latestRecipientsCache = BlobDataSource.simpleJSON<List<AccountAddress>>(context, LATEST_RECIPIENTS)

    private val _decryptedCommentFlow = MutableEffectFlow<Unit>()
    val decryptedCommentFlow = _decryptedCommentFlow.shareIn(scope, SharingStarted.WhileSubscribed(), 1)

    private val encryptedPrefs: SharedPreferences by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { Security.pref(context, KEY_ALIAS, NAME) }

    private fun keyDecryptedComment(txId: String): String {
        return "tx_$txId"
    }

    fun addSpam(accountId: String, testnet: Boolean, events: List<AccountEvent>) {
        databaseSource.addSpam(accountId, testnet, events)
    }

    fun removeSpam(accountId: String, testnet: Boolean, eventId: String) {
        databaseSource.removeSpam(accountId, testnet, eventId)
    }

    fun getSpam(accountId: String, testnet: Boolean): List<AccountEvent> {
        return databaseSource.getSpam(accountId, testnet)
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

    fun getEvents(key: String): AccountEvents? {
        return eventsCache.getCache(key)
    }

    fun setEvents(key: String, events: AccountEvents) {
        eventsCache.setCache(key, events)
    }

    fun getLatestRecipients(key: String): List<AccountAddress>? {
        val list = latestRecipientsCache.getCache(key)
        if (list.isNullOrEmpty()) {
            return null
        }
        return list
    }

    fun setLatestRecipients(key: String, recipients: List<AccountAddress>) {
        latestRecipientsCache.setCache(key, recipients)
    }
}