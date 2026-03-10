package com.tonapps.wallet.data.events.source

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.tonapps.extensions.MutableEffectFlow
import com.tonapps.security.Security
import com.tonapps.wallet.api.entity.value.BlockchainAddress
import com.tonapps.wallet.api.tron.entity.TronEventEntity
import com.tonapps.wallet.data.core.BlobDataSource
import com.tonapps.wallet.data.events.entities.LatestRecipientEntity
import com.tonapps.wallet.data.events.tx.TxEvents
import com.tonapps.wallet.data.events.tx.model.TxEvent
import io.tonapi.models.AccountEvent
import io.tonapi.models.AccountEvents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn

internal class LocalDataSource(
    scope: CoroutineScope,
    context: Context
) {

    companion object {
        private const val NAME = "events"
        private const val LATEST_RECIPIENTS = "latest_recipients_v2"
        private const val KEY_ALIAS = "_com_tonapps_events_master_key_"
    }

    private val txEvents = BlobDataSource.simple<TxEvents>(context, "tx_events")
    private val databaseSource: DatabaseSource = DatabaseSource(scope, context)
    private val eventsCache = BlobDataSource.simpleJSON<AccountEvents>(context, "events")
    private val tronEventsCache = BlobDataSource.simpleJSON<List<TronEventEntity>>(context, "tron_events")
    private val latestRecipientsCache = BlobDataSource.simpleJSON<List<LatestRecipientEntity>>(context, LATEST_RECIPIENTS)

    private val _decryptedCommentFlow = MutableStateFlow(emptyMap<String, String>())
    val decryptedCommentFlow = _decryptedCommentFlow.stateIn(scope, SharingStarted.WhileSubscribed(), emptyMap())

    private val encryptedPrefs: SharedPreferences by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { Security.pref(context, KEY_ALIAS, NAME) }

    fun getTxEvents(account: BlockchainAddress): List<TxEvent> {
        val events = txEvents.getCache(account.key)?.events
        return events ?: listOf()
    }

    fun clearTxEvents(account: BlockchainAddress) {
        txEvents.clearCache(account.key)
    }

    fun setTxEvents(account: BlockchainAddress, events: List<TxEvent>) {
        if (events.isEmpty()) {
            clearTxEvents(account)
        } else {
            txEvents.setCache(account.key, TxEvents(events))
        }
    }

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
            _decryptedCommentFlow.value += (txId to comment)
        }
    }

    fun getEvents(key: String): AccountEvents? {
        return eventsCache.getCache(key)
    }

    fun getTronEvents(key: String): List<TronEventEntity>? {
        return tronEventsCache.getCache(key)
    }

    fun setTronEvents(key: String, events: List<TronEventEntity>) {
        tronEventsCache.setCache(key, events)
    }

    fun setEvents(key: String, events: AccountEvents) {
        eventsCache.setCache(key, events)
    }

    fun getLatestRecipients(key: String): List<LatestRecipientEntity>? {
        val list = latestRecipientsCache.getCache(key)
        if (list.isNullOrEmpty()) {
            return null
        }
        return list
    }

    fun setLatestRecipients(key: String, recipients: List<LatestRecipientEntity>) {
        latestRecipientsCache.setCache(key, recipients)
    }
}