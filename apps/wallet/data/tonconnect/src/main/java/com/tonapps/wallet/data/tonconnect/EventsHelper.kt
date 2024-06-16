package com.tonapps.wallet.data.tonconnect

import android.content.SharedPreferences
import com.tonapps.extensions.base64
import com.tonapps.extensions.string
import com.tonapps.network.SSEvent
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.tonconnect.entities.DAppEntity
import com.tonapps.wallet.data.tonconnect.entities.DAppEventEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

internal class EventsHelper(
    private val prefs: SharedPreferences,
    private val accountRepository: AccountRepository,
    private val api: API
) {

    private val recentlyReceivedEventIds = ArrayDeque<String>(10)

    @OptIn(ExperimentalCoroutinesApi::class)
    fun flow(appsFlow: Flow<List<DAppEntity>>) = appsFlow.flatMapLatest { apps ->
        val publicKeys = apps.map { it.publicKeyHex }
        api.tonconnectEvents(publicKeys, prefs.string(LAST_EVENT_ID_KEY)).map { event ->
            processEvent(apps, event)
        }
    }.filterNotNull()

    private suspend fun processEvent(apps: List<DAppEntity>, event: SSEvent): DAppEventEntity? {
        if (!processEventId(event.id)) {
            return null
        }

        val from = event.json.getString("from")
        val message = event.json.getString("message")
        val app = apps.find { it.clientId == from } ?: return null
        val wallet = accountRepository.getWalletById(app.walletId) ?: return null
        return DAppEventEntity(wallet, app, message.base64)
    }

    private fun processEventId(id: String?): Boolean {
        if (id == null || recentlyReceivedEventIds.contains(id)) {
            return false
        }
        recentlyReceivedEventIds.add(id)
        prefs.string(LAST_EVENT_ID_KEY, id)
        return true
    }

    private companion object {
        private const val LAST_EVENT_ID_KEY = "last_event_id"
    }

}