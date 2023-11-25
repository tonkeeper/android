package com.tonkeeper.core.history

import android.icu.text.SimpleDateFormat
import com.tonkeeper.R
import com.tonkeeper.api.amount
import com.tonkeeper.api.description
import com.tonkeeper.api.iconURL
import com.tonkeeper.api.imageURL
import com.tonkeeper.api.jettonPreview
import com.tonkeeper.api.nameOrAddress
import com.tonkeeper.api.nft.NftRepository
import com.tonkeeper.api.title
import com.tonkeeper.api.ton
import com.tonkeeper.core.Coin
import com.tonkeeper.core.history.list.item.HistoryActionItem
import com.tonkeeper.core.history.list.item.HistoryHeaderItem
import com.tonkeeper.core.history.list.item.HistoryItem
import com.tonkeeper.helper.DateFormat
import io.tonapi.models.AccountEvent
import io.tonapi.models.AccountEvents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ton.SupportedCurrency
import ton.SupportedTokens
import ton.wallet.Wallet
import ton.wallet.WalletInfo
import java.util.Calendar
import java.util.Date

object HistoryHelper {

    private val nftRepository = NftRepository()

    private val calendar = Calendar.getInstance()
    private val dateFormat1 = SimpleDateFormat("h:mm a")
    private val dateFormat2 = SimpleDateFormat("MMM dd, h:mm a")

    suspend fun mapping(
        wallet: Wallet,
        events: AccountEvents
    ): List<HistoryItem> {
        return mapping(wallet, events.events)
    }

    private fun formatDate(timestamp: Long): String {
        val date = Date(timestamp * 1000)
        val timestampDate = calendar.apply { time = date }
        return if (calendar.get(Calendar.MONTH) == timestampDate.get(Calendar.MONTH)) {
            dateFormat1.format(date)
        } else {
            dateFormat2.format(date)
        }
    }

    suspend fun mapping(
        wallet: Wallet,
        events: List<AccountEvent>
    ): List<HistoryItem> = withContext(Dispatchers.IO) {
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

            val date = formatDate(event.timestamp)

            val action = event.actions.firstOrNull() ?: continue

            val simplePreview = action.simplePreview
            if (action.jettonSwap != null) {
                val jettonSwap = action.jettonSwap!!
                val jettonPreview = jettonSwap.jettonPreview!!
                val amount = Coin.parseFloat(jettonSwap.amount, jettonPreview.decimals)
                val isOut = jettonSwap.amountOut != ""
                val value: String
                val value2: String
                val tokenCode: String

                if (!isOut) {
                    tokenCode = SupportedCurrency.TON.code
                    value = Coin.format(value = jettonSwap.ton)
                    value2 = Coin.format(value = amount, decimals = jettonPreview.decimals)
                } else {
                    tokenCode = jettonPreview.symbol
                    value = Coin.format(value = amount, decimals = jettonPreview.decimals)
                    value2 = Coin.format(currency = SupportedCurrency.TON, value = jettonSwap.ton)
                }

                val item = HistoryActionItem(
                    iconURL = "",
                    action = HistoryActionItem.Action.Swap,
                    title = simplePreview.name,
                    subtitle = jettonSwap.router.nameOrAddress,
                    timestamp = event.timestamp,
                    comment = "",
                    value = value,
                    value2 = "- $value2",
                    tokenCode = tokenCode,
                    date = date,
                )
                items.add(item)
            } else if (action.jettonTransfer != null) {
                val jettonTransfer = action.jettonTransfer!!
                val isOut = !wallet.isMyAddress(jettonTransfer.recipient?.address ?: "")
                val value = simplePreview.value ?: ""

                val itemAction: HistoryActionItem.Action
                val iconURL: String?
                val subtitle: String

                if (isOut) {
                    itemAction = HistoryActionItem.Action.Send
                    iconURL = jettonTransfer.recipient?.iconURL ?: ""
                    subtitle = jettonTransfer.recipient?.nameOrAddress ?: ""
                } else {
                    itemAction = HistoryActionItem.Action.Received
                    iconURL = jettonTransfer.sender?.iconURL ?: ""
                    subtitle = jettonTransfer.sender?.nameOrAddress ?: ""
                }

                val item = HistoryActionItem(
                    iconURL = iconURL,
                    action = itemAction,
                    title = simplePreview.name,
                    subtitle = subtitle,
                    timestamp = event.timestamp,
                    comment = jettonTransfer.comment,
                    value = value,
                    tokenCode = "",
                    date = date,
                )
                items.add(item)
            } else if (action.tonTransfer != null) {
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
                    value = Coin.format(value = tonTransfer.amount),
                    tokenCode = SupportedTokens.TON.code,
                    date = date,
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
                    value = Coin.format(value = smartContractExec.tonAttached),
                    tokenCode = SupportedTokens.TON.code,
                    date = date,
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
                    nftCollection = nftItem.description,
                    tokenCode = "NFT",
                    date = date,
                )
                items.add(item)

            } else {
                val item = HistoryActionItem(
                    iconURL = "",
                    action = HistoryActionItem.Action.Received,
                    title = simplePreview.name,
                    subtitle = simplePreview.description,
                    timestamp = event.timestamp,
                    value = simplePreview.value ?: "",
                    tokenCode = SupportedTokens.TON.code,
                    date = date,
                )
                items.add(item)
            }
        }

        return@withContext items
    }
}