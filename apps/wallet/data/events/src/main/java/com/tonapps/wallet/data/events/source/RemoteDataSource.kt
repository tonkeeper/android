package com.tonapps.wallet.data.events.source

import com.tonapps.blockchain.ton.extensions.equalsAddress
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.events.entities.LatestRecipientEntity
import com.tonapps.wallet.data.events.isOutTransfer
import com.tonapps.wallet.data.events.recipient
import io.tonapi.models.AccountEvents

internal class RemoteDataSource(
    private val api: API
) {

    fun get(
        accountId: String,
        testnet: Boolean,
        beforeLt: Long? = null,
        limit: Int = 12
    ): AccountEvents? = api.getEvents(accountId, testnet, beforeLt, limit)

    suspend fun getSingle(eventId: String, testnet: Boolean) = api.getSingleEvent(eventId, testnet)

    fun getLatestRecipients(accountId: String, testnet: Boolean): List<LatestRecipientEntity> {
        val events = api.getEvents(
            accountId = accountId,
            testnet = testnet,
            limit = 100
        )?.events ?: return emptyList()

        val recipients = mutableListOf<LatestRecipientEntity>()

        events.forEach { event ->
            event.actions.forEach { action ->
                if (action.isOutTransfer(accountId) && action.recipient != null && action.recipient!!.address != accountId) {
                    recipients.add(
                        LatestRecipientEntity(
                            account = action.recipient!!,
                            timestamp = event.timestamp
                        )
                    )
                }
            }
        }

        return recipients.filter {
            it.account.isWallet && !it.account.address.equalsAddress(accountId)
        }.distinctBy { it.account.address }.take(6)
    }
}