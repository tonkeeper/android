package com.tonkeeper.core.history

import android.icu.text.SimpleDateFormat
import com.tonkeeper.App
import com.tonkeeper.Global
import com.tonkeeper.R
import com.tonkeeper.api.address
import com.tonkeeper.api.amount
import com.tonkeeper.api.description
import com.tonkeeper.api.fee
import com.tonkeeper.api.iconURL
import com.tonkeeper.api.imageURL
import com.tonkeeper.api.jettonPreview
import com.tonkeeper.api.nameOrAddress
import com.tonkeeper.api.nft.NftRepository
import com.tonkeeper.api.title
import com.tonkeeper.api.ton
import com.tonkeeper.core.Coin
import com.tonkeeper.core.currency.from
import com.tonkeeper.core.history.list.item.HistoryItem
import com.tonkeeper.event.WalletStateUpdateEvent
import com.tonkeeper.helper.DateFormat
import core.EventBus
import core.network.Network
import io.tonapi.models.AccountAddress
import io.tonapi.models.AccountEvent
import io.tonapi.models.AccountEvents
import io.tonapi.models.Action
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import ton.SupportedCurrency
import ton.SupportedTokens
import ton.extensions.toUserFriendly
import ton.wallet.Wallet
import uikit.list.ListCell
import java.util.Calendar
import java.util.Date

object HistoryHelper {

    private const val sseUrlPrefix = "https://tonapi.io/v2/sse"

    private val nftRepository = NftRepository()

    private val calendar = Calendar.getInstance()
    private val dateFormat1 = SimpleDateFormat("h:mm a")
    private val dateFormat2 = SimpleDateFormat("MMM dd, h:mm a")

    fun subscribe(scope: CoroutineScope, accountId: String) {
        val mempool = Network.subscribe("${sseUrlPrefix}/mempool?accounts=${accountId}")
        val tx = Network.subscribe("${sseUrlPrefix}/accounts/transactions?accounts=${accountId}")

        merge(mempool, tx).onEach {
            EventBus.post(WalletStateUpdateEvent)
        }.launchIn(scope)
    }

    suspend fun mapping(
        wallet: Wallet,
        event: AccountEvent,
        groupByDate: Boolean = true
    ): List<HistoryItem> {
        return mapping(wallet, arrayListOf(event), groupByDate)
    }

    suspend fun mapping(
        wallet: Wallet,
        events: AccountEvents,
        groupByDate: Boolean = true
    ): List<HistoryItem> {
        return mapping(wallet, events.events, groupByDate)
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
        events: List<AccountEvent>,
        groupByDate: Boolean = true
    ): List<HistoryItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<HistoryItem>()

        for ((index, event) in events.withIndex()) {
            val pending = event.inProgress
            val prevEvent = events.getOrNull(index - 1)

            if (groupByDate) {
                if (prevEvent == null) {
                    val isToday = DateFormat.isToday(event.timestamp)
                    val isYesterday = DateFormat.isYesterday(event.timestamp)
                    if (isToday) {
                        items.add(HistoryItem.Header(R.string.today))
                    } else if (isYesterday) {
                        items.add(HistoryItem.Header(R.string.yesterday))
                    } else {
                        items.add(HistoryItem.Header(event.timestamp))
                    }
                } else {
                    val sameDay = DateFormat.isSameDay(prevEvent.timestamp, event.timestamp)
                    val sameMonth = DateFormat.isSameMonth(prevEvent.timestamp, event.timestamp)
                    val sameYear = DateFormat.isSameYear(prevEvent.timestamp, event.timestamp)
                    if (!sameDay) {
                        items.add(HistoryItem.Header(event.timestamp))
                    }
                }
            }

            val actions = event.actions
            val fee = event.fee
            val currency = App.settings.currency

            val feeInCurrency = from(SupportedTokens.TON, wallet.accountId)
                .value(fee)
                .to(currency)


            for ((actionIndex, action) in actions.withIndex()) {
                val item = action(event.eventId, wallet, action, event.timestamp)
                items.add(item.copy(
                    pending = pending,
                    position = ListCell.getPosition(actions.size, actionIndex),
                    fee = Coin.format(value = event.fee, decimals = 9),
                    feeInCurrency = Coin.format(currency = currency, value = feeInCurrency, decimals = 12),
                ))
            }

            items.add(HistoryItem.Space)
        }

        return@withContext items
    }

    private suspend fun action(
        txId: String,
        wallet: Wallet,
        action: Action,
        timestamp: Long,
    ): HistoryItem.Action {
        val currency = App.settings.currency

        val simplePreview = action.simplePreview
        val date = formatDate(timestamp)
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

            val inCurrency = from(jettonSwap.jettonPreview!!.address, wallet.accountId)
                .value(jettonSwap.amount.toFloat())
                .to(currency)

            return HistoryItem.Action(
                txId = txId,
                iconURL = "",
                action = HistoryItem.Action.Type.Swap,
                title = simplePreview.name,
                subtitle = jettonSwap.router.nameOrAddress,
                comment = "",
                value = value,
                value2 = "- $value2",
                tokenCode = tokenCode,
                coinIconUrl = jettonPreview.image,
                timestamp = timestamp,
                date = date,
                isOut = isOut,
                currency = Coin.format(currency = currency, value = inCurrency, decimals = 9),
            )
        } else if (action.jettonTransfer != null) {
            val jettonTransfer = action.jettonTransfer!!
            val isOut = !wallet.isMyAddress(jettonTransfer.recipient?.address ?: "")
            val value = simplePreview.value ?: ""


            val itemAction: HistoryItem.Action.Type
            val accountAddress: AccountAddress?

            if (isOut) {
                itemAction = HistoryItem.Action.Type.Send
                accountAddress = jettonTransfer.recipient
            } else {
                itemAction = HistoryItem.Action.Type.Received
                accountAddress = jettonTransfer.sender
            }

            val inCurrency = from(jettonTransfer.jetton.address, wallet.accountId)
                .value(jettonTransfer.amount.toFloat())
                .to(currency)


            return HistoryItem.Action(
                txId = txId,
                iconURL = accountAddress?.iconURL ?: "",
                action = itemAction,
                title = simplePreview.name,
                subtitle = accountAddress?.nameOrAddress ?: "",
                comment = jettonTransfer.comment,
                value = value,
                tokenCode = "",
                coinIconUrl = jettonTransfer.jetton.image,
                timestamp = timestamp,
                date = date,
                isOut = isOut,
                address = accountAddress?.address?.toUserFriendly(),
                addressName = accountAddress?.name,
                currency = Coin.format(currency = currency, value = inCurrency, decimals = 9),
            )
        } else if (action.tonTransfer != null) {
            val tonTransfer = action.tonTransfer!!

            val isOut = !wallet.isMyAddress(tonTransfer.recipient.address)

            val itemAction: HistoryItem.Action.Type
            val accountAddress: AccountAddress

            if (isOut) {
                itemAction = HistoryItem.Action.Type.Send
                accountAddress = tonTransfer.recipient
            } else {
                itemAction = HistoryItem.Action.Type.Received
                accountAddress = tonTransfer.sender
            }

            val inCurrency = from(SupportedTokens.TON, wallet.accountId)
                .value(tonTransfer.amount)
                .to(currency)

            return HistoryItem.Action(
                txId = txId,
                iconURL = accountAddress.iconURL,
                action = itemAction,
                title = simplePreview.name,
                subtitle = accountAddress.nameOrAddress,
                comment = tonTransfer.comment,
                value = Coin.format(value = tonTransfer.amount),
                tokenCode = SupportedTokens.TON.code,
                coinIconUrl = Global.tonCoinUrl,
                timestamp = timestamp,
                date = date,
                isOut = isOut,
                address = accountAddress.address.toUserFriendly(),
                addressName = accountAddress.name,
                currency = Coin.format(currency = currency, value = inCurrency, decimals = 9),
            )
        } else if (action.smartContractExec != null) {
            val smartContractExec = action.smartContractExec!!
            val executor = smartContractExec.executor
            return HistoryItem.Action(
                txId = txId,
                iconURL = executor.iconURL,
                action = HistoryItem.Action.Type.CallContract,
                title = simplePreview.name,
                subtitle = executor.nameOrAddress,
                value = Coin.format(value = smartContractExec.tonAttached),
                tokenCode = SupportedTokens.TON.code,
                timestamp = timestamp,
                date = date,
                isOut = true,
            )
        } else if (action.nftItemTransfer != null) {
            val nftItemTransfer = action.nftItemTransfer!!
            val isOut = !wallet.isMyAddress(nftItemTransfer.recipient?.address ?: "-")

            val itemAction: HistoryItem.Action.Type
            val iconURL: String?
            val subtitle: String

            if (isOut) {
                itemAction = HistoryItem.Action.Type.NftSend
                iconURL = nftItemTransfer.recipient?.iconURL
                subtitle = nftItemTransfer.recipient?.nameOrAddress ?: ""
            } else {
                itemAction = HistoryItem.Action.Type.NftReceived
                iconURL = nftItemTransfer.sender?.iconURL
                subtitle = nftItemTransfer.sender?.nameOrAddress ?: ""
            }

            val nftItem = nftRepository.getItem(nftItemTransfer.nft)

            return HistoryItem.Action(
                txId = txId,
                iconURL = iconURL,
                action = itemAction,
                title = simplePreview.name,
                subtitle = subtitle,
                value = "NFT",
                nftImageURL = nftItem.imageURL,
                nftTitle = nftItem.title,
                nftCollection = nftItem.description,
                tokenCode = "NFT",
                timestamp = timestamp,
                date = date,
                isOut = isOut,
            )
        } else {
            return HistoryItem.Action(
                txId = txId,
                iconURL = "",
                action = HistoryItem.Action.Type.Received,
                title = simplePreview.name,
                subtitle = simplePreview.description,
                value = simplePreview.value ?: "",
                tokenCode = SupportedTokens.TON.code,
                timestamp = timestamp,
                date = date,
                isOut = false,
            )
        }
    }
}