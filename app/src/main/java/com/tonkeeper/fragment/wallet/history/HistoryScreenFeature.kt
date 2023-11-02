package com.tonkeeper.fragment.wallet.history

import android.util.Log
import com.tonkeeper.App
import com.tonkeeper.R
import com.tonkeeper.api.description
import com.tonkeeper.api.event.EventRepository
import com.tonkeeper.api.iconURL
import com.tonkeeper.api.imageURL
import com.tonkeeper.api.nameOrAddress
import com.tonkeeper.api.nft.NftRepository
import com.tonkeeper.api.title
import com.tonkeeper.core.Coin
import com.tonkeeper.fragment.wallet.history.list.item.HistoryActionItem
import com.tonkeeper.fragment.wallet.history.list.item.HistoryHeaderItem
import com.tonkeeper.fragment.wallet.history.list.item.HistoryItem
import com.tonkeeper.helper.DateFormat
import core.QueueScope
import ton.WalletInfo
import uikit.mvi.AsyncState
import uikit.mvi.UiFeature
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class HistoryScreenFeature: UiFeature<HistoryScreenState, HistoryScreenEffect>(HistoryScreenState()) {

    private val queueScope = QueueScope(Dispatchers.IO)
    private val eventRepository = EventRepository()
    private val nftRepository = NftRepository()

    init {
        requestEventsState()
        syncEvents()
    }

    private fun syncEvents() {
        queueScope.submit {
            updateUiState { currentState ->
                currentState.copy(
                    asyncState = AsyncState.Loading
                )
            }

            val wallet = getWallet() ?: return@submit

            eventRepository.sync(wallet.address)

            updateEventsState()
        }
    }

    private suspend fun updateEventsState() {
        val wallet = getWallet() ?: return
        val events = eventRepository.get(wallet.address)
        val items = mutableListOf<HistoryItem>()

        for ((index, event) in events.withIndex()) {
            val prevEvent = events.getOrNull(index - 1)
            if (prevEvent == null) {
                val isToday = DateFormat.isToday(event.timestamp)
                val isYesterday = DateFormat.isYesterday(event.timestamp)
                if (isToday) {
                    items.add(HistoryHeaderItem(R.string.today))
                } else if (isYesterday) {
                    items.add(HistoryHeaderItem(R.string.yesterday))
                } else {
                    items.add(HistoryHeaderItem(event.timestamp))
                }
            } else {
                val sameDay = DateFormat.isSameDay(prevEvent.timestamp, event.timestamp)
                val sameMonth = DateFormat.isSameMonth(prevEvent.timestamp, event.timestamp)
                val sameYear = DateFormat.isSameYear(prevEvent.timestamp, event.timestamp)
                if (!sameDay) {
                    items.add(HistoryHeaderItem(event.timestamp))
                }
            }

            val action = event.actions.firstOrNull() ?: continue

            val simplePreview = action.simplePreview
            if (action.tonTransfer != null) {
                val tonTransfer = action.tonTransfer!!

                val isOut = !wallet.isMyAddress(tonTransfer.recipient.address)

                val itemAction: HistoryActionItem.Action
                val iconURL: String?
                val subtitle: String

                if (isOut) {
                    itemAction = HistoryActionItem.Action.Send
                    iconURL = tonTransfer.recipient.iconURL
                    subtitle = tonTransfer.recipient.nameOrAddress
                } else {
                    itemAction = HistoryActionItem.Action.Received
                    iconURL = tonTransfer.sender.iconURL
                    subtitle = tonTransfer.sender.nameOrAddress
                }

                val item = HistoryActionItem(
                    iconURL = iconURL,
                    action = itemAction,
                    title = simplePreview.name,
                    subtitle = subtitle,
                    timestamp = event.timestamp,
                    comment = tonTransfer.comment,
                    value = Coin.format(value = tonTransfer.amount)
                )
                items.add(item)
            } else if (action.smartContractExec != null) {
                val smartContractExec = action.smartContractExec!!
                val executor = smartContractExec.executor
                val item = HistoryActionItem(
                    iconURL = executor.iconURL,
                    action = HistoryActionItem.Action.CallContract,
                    title = simplePreview.name,
                    subtitle = executor.nameOrAddress,
                    timestamp = event.timestamp,
                    value = Coin.format(value = smartContractExec.tonAttached)
                )
                items.add(item)
            } else if (action.nftItemTransfer != null) {
                val nftItemTransfer = action.nftItemTransfer!!
                val isOut = !wallet.isMyAddress(nftItemTransfer.recipient?.address ?: "-")

                val itemAction: HistoryActionItem.Action
                val iconURL: String?
                val subtitle: String

                if (isOut) {
                    itemAction = HistoryActionItem.Action.NftSend
                    iconURL = nftItemTransfer.recipient?.iconURL
                    subtitle = nftItemTransfer.recipient?.nameOrAddress ?: ""
                } else {
                    itemAction = HistoryActionItem.Action.NftReceived
                    iconURL = nftItemTransfer.sender?.iconURL
                    subtitle = nftItemTransfer.sender?.nameOrAddress ?: ""
                }

                val nftItem = nftRepository.getItem(nftItemTransfer.nft)

                val item = HistoryActionItem(
                    iconURL = iconURL,
                    action = itemAction,
                    title = simplePreview.name,
                    subtitle = subtitle,
                    timestamp = event.timestamp,
                    value = "NFT",
                    nftImageURL = nftItem.imageURL,
                    nftTitle = nftItem.title,
                    nftCollection = nftItem.description
                )
                items.add(item)

            } else {
                val item = HistoryActionItem(
                    iconURL = "",
                    action = HistoryActionItem.Action.Received,
                    title = simplePreview.name,
                    subtitle = simplePreview.description,
                    timestamp = event.timestamp,
                    value = simplePreview.value ?: ""
                )
                items.add(item)
            }
        }
        updateUiState { currentState ->
            currentState.copy(
                asyncState = AsyncState.Default,
                items = items
            )
        }
    }

    private fun requestEventsState() {
        updateUiState { currentState ->
            currentState.copy(
                asyncState = AsyncState.Loading
            )
        }

        queueScope.submit {
            updateEventsState()
        }
    }

    private suspend fun getWallet(): WalletInfo? = withContext(Dispatchers.IO) {
        App.walletManager.getWalletInfo()
    }

    override fun onCleared() {
        super.onCleared()
        queueScope.cancel()
    }

}