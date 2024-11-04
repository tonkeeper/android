package com.tonapps.wallet.data.events.source

import android.util.Log
import com.tonapps.blockchain.ton.extensions.equalsAddress
import com.tonapps.blockchain.ton.extensions.toRawAddress
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.events.isOutTransfer
import com.tonapps.wallet.data.events.recipient
import io.tonapi.models.AccountAddress
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

    fun getLatestRecipients(accountId: String, testnet: Boolean): List<AccountAddress> {
        val actions = api.getEvents(
            accountId = accountId,
            testnet = testnet,
            limit = 100
        )?.events?.map { it.actions }?.flatten() ?: return emptyList()

        val recipients = actions.filter { it.isOutTransfer(accountId) }.mapNotNull { it.recipient }

        return recipients.filter {
            it.isWallet && !it.address.equalsAddress(accountId) //  && !it.name.isNullOrEmpty()
        }.distinctBy { it.address }.take(6)
    }
}