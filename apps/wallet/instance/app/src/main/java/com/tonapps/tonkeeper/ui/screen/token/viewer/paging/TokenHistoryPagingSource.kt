package com.tonapps.tonkeeper.ui.screen.token.viewer.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.tonapps.blockchain.contract.Blockchain
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.tonkeeper.core.history.ActionOptions
import com.tonapps.tonkeeper.core.history.HistoryHelper
import com.tonapps.tonkeeper.core.history.list.item.HistoryItem
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.events.EventsRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class TokenHistoryPagingSource(
    private val wallet: WalletEntity,
    private val tokenAddress: String,
    private val blockchain: Blockchain,
    private val tronAddress: String?,
    private val tronUsdtEnabled: Boolean,
    private val eventsRepository: EventsRepository,
    private val accountRepository: AccountRepository,
    private val historyHelper: HistoryHelper,
    private val settingsRepository: SettingsRepository,
    private val api: API,
) : PagingSource<Long, HistoryItem>() {

    override suspend fun load(
        params: LoadParams<Long>
    ): LoadResult<Long, HistoryItem> = withContext(Dispatchers.IO) {
        try {
            val beforeKey = params.key
            val items = if (blockchain === Blockchain.TRON) {
                loadTron(beforeKey)
            } else {
                loadTon(beforeKey)
            }

            val nextKey = extractNextKey(items)
            LoadResult.Page(
                data = items,
                prevKey = null,
                nextKey = nextKey,
            )
        } catch (e: Throwable) {
            LoadResult.Error(e)
        }
    }

    private suspend fun loadTon(beforeLt: Long?): List<HistoryItem> {
        val accountEvents = eventsRepository.loadForToken(
            tokenAddress, wallet.accountId, wallet.network, beforeLt
        ) ?: return emptyList()

        val options = ActionOptions(
            safeMode = settingsRepository.isSafeModeEnabled(wallet.network),
            hiddenBalances = settingsRepository.hiddenBalances,
            tronEnabled = tronUsdtEnabled,
        )

        return historyHelper.mapping(
            wallet = wallet,
            events = accountEvents.events,
            options = options,
        )
    }

    private suspend fun loadTron(maxTimestamp: Long?): List<HistoryItem> {
        val address = tronAddress ?: return emptyList()
        val tonProofToken = accountRepository.requestTonProofToken(wallet) ?: return emptyList()
        val tronEvents = eventsRepository.loadTronEvents(
            address, tonProofToken, maxTimestamp
        ) ?: return emptyList()

        val options = ActionOptions(
            safeMode = settingsRepository.isSafeModeEnabled(wallet.network),
            hiddenBalances = settingsRepository.hiddenBalances,
        )

        return historyHelper.tronMapping(
            wallet = wallet,
            tronAddress = address,
            events = tronEvents,
            options = options,
        )
    }

    private fun extractNextKey(items: List<HistoryItem>): Long? {
        val lastEvent = items.lastOrNull { it is HistoryItem.Event } as? HistoryItem.Event
            ?: return null
        return if (blockchain === Blockchain.TRON) {
            if (lastEvent.timestamp > 0) lastEvent.timestamp else null
        } else {
            if (lastEvent.lt > 0) lastEvent.lt else null
        }
    }

    override fun getRefreshKey(state: PagingState<Long, HistoryItem>): Long? = null
}
