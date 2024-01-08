package com.tonkeeper.core.history

import android.icu.text.SimpleDateFormat
import com.tonapps.tonkeeperx.R
import com.tonkeeper.App
import com.tonkeeper.Global
import com.tonkeeper.api.amount
import com.tonkeeper.api.description
import com.tonkeeper.api.fee
import com.tonkeeper.api.iconURL
import com.tonkeeper.api.imageURL
import com.tonkeeper.api.jettonPreview
import com.tonkeeper.api.nameOrAddress
import com.tonkeeper.api.nft.NftRepository
import com.tonkeeper.api.parsedAmount
import com.tonkeeper.api.shortAddress
import com.tonkeeper.api.title
import com.tonkeeper.api.ton
import com.tonkeeper.core.Coin
import com.tonkeeper.core.formatter.CurrencyFormatter
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
import io.tonapi.models.ActionSimplePreview
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

    const val EVENT_LIMIT = 20

    const val MINUS_SYMBOL = "-"
    const val PLUS_SYMBOL = "+"

    private val nftRepository = NftRepository()

    private val calendar = Calendar.getInstance()
    private val dateFormat1 = SimpleDateFormat("h:mm a")
    private val dateFormat2 = SimpleDateFormat("MMM dd, h:mm a")

    // dirty hack to remove trailing zeros
    private val amountModifier = { value: String ->
        val decimalSeparator = CurrencyFormatter.monetaryDecimalSeparator
        val r = value.removeSuffix(decimalSeparator + "00")
        if (r == "") {
            "0${decimalSeparator}01"
        } else {
            r
        }
    }


    fun withLoadingItem(items: List<HistoryItem>): List<HistoryItem> {
        val last = items.lastOrNull()
        if (last is HistoryItem.Loader) {
            return items
        }

        val newItems = items.toMutableList()
        newItems.add(HistoryItem.Loader(newItems.size))
        return newItems
    }

    fun removeLoadingItem(items: List<HistoryItem>): List<HistoryItem> {
        val last = items.lastOrNull()
        if (last is HistoryItem.Loader) {
            val newItems = items.toMutableList()
            newItems.removeAt(newItems.size - 1)
            return newItems
        }
        return items
    }

    fun subscribe(scope: CoroutineScope, accountId: String) {
        val mempool = Network.subscribe("https://keeper.tonapi.io/v2/sse/mempool?accounts=${accountId}")
        val tx = Network.subscribe("https://tonapi.io/v2/sse/accounts/transactions?accounts=${accountId}")

        merge(mempool, tx).onEach {
            EventBus.post(WalletStateUpdateEvent)
        }.launchIn(scope)
    }

    suspend fun mapping(
        wallet: Wallet,
        event: AccountEvent,
        groupByDate: Boolean = true,
        removeDate: Boolean = false,
    ): List<HistoryItem> {
        return mapping(wallet, arrayListOf(event), groupByDate, removeDate)
    }

    suspend fun mapping(
        wallet: Wallet,
        events: AccountEvents,
        groupByDate: Boolean = true,
        removeDate: Boolean = false,
    ): List<HistoryItem> {
        return mapping(wallet, events.events, groupByDate, removeDate)
    }

    private fun formatDate(timestamp: Long): String {
        if (timestamp == 0L) {
            return ""
        }
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
        groupByDate: Boolean = true,
        removeDate: Boolean = false,
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


            val chunkItems = mutableListOf<HistoryItem>()
            for ((actionIndex, action) in actions.withIndex()) {
                val timestamp = if (removeDate) 0 else event.timestamp
                val item = action(event.eventId, wallet, action, timestamp)
                val feeAmount = Coin.toCoins(event.fee)
                chunkItems.add(item.copy(
                    pending = pending,
                    position = ListCell.getPosition(actions.size, actionIndex),
                    fee = CurrencyFormatter.format(SupportedCurrency.TON.code, feeAmount),
                    feeInCurrency = CurrencyFormatter.formatFiat(feeInCurrency),
                    lt = event.lt,
                ))
            }

            if (chunkItems.size > 0) {
                items.addAll(chunkItems)
                items.add(HistoryItem.Space(index))
            }
        }

        return@withContext items
    }

    private suspend fun action(
        txId: String,
        wallet: Wallet,
        action: Action,
        timestamp: Long,
    ): HistoryItem.Event {
        val currency = App.settings.currency

        val simplePreview = action.simplePreview
        val date = formatDate(timestamp)

        if (action.jettonSwap != null) {
            val jettonSwap = action.jettonSwap!!
            val jettonPreview = jettonSwap.jettonPreview!!
            val symbol = jettonPreview.symbol
            val amount = Coin.parseFloat(jettonSwap.amount, jettonPreview.decimals)
            val tonFromJetton = Coin.toCoins(jettonSwap.ton)

            val isOut = jettonSwap.amountOut != ""
            val value: String
            val value2: String
            val tokenCode: String

            if (!isOut) {
                tokenCode = SupportedCurrency.TON.code
                value = CurrencyFormatter.format(SupportedCurrency.TON.code, tonFromJetton, amountModifier)
                value2 = CurrencyFormatter.format(symbol, amount, amountModifier)
            } else {
                tokenCode = symbol
                value = CurrencyFormatter.format(symbol, amount, amountModifier)
                value2 = CurrencyFormatter.format(SupportedCurrency.TON.code, tonFromJetton, amountModifier)
            }

            val inCurrency = from(jettonSwap.jettonPreview!!.address, wallet.accountId)
                .value(jettonSwap.amount.toFloat())
                .to(currency)

            return HistoryItem.Event(
                txId = txId,
                iconURL = "",
                action = ActionType.Swap,
                title = simplePreview.name,
                subtitle = jettonSwap.router.nameOrAddress,
                comment = "",
                value = withPlusPrefix(value),
                value2 = withMinusPrefix(value2),
                tokenCode = tokenCode,
                coinIconUrl = jettonPreview.image,
                timestamp = timestamp,
                date = date,
                isOut = isOut,
                currency = CurrencyFormatter.formatFiat(inCurrency),
            )
        } else if (action.jettonTransfer != null) {
            val jettonTransfer = action.jettonTransfer!!
            val symbol = jettonTransfer.jetton.symbol
            val isOut = !wallet.isMyAddress(jettonTransfer.recipient?.address ?: "")

            val amount = Coin.parseFloat(jettonTransfer.amount, jettonTransfer.jetton.decimals)
            var value = CurrencyFormatter.format(symbol, amount, amountModifier)

            val itemAction: ActionType
            val accountAddress: AccountAddress?

            if (isOut) {
                itemAction = ActionType.Send
                accountAddress = jettonTransfer.recipient
                value = withMinusPrefix(value)
            } else {
                itemAction = ActionType.Received
                accountAddress = jettonTransfer.sender
                value = withPlusPrefix(value)
            }

            val inCurrency = from(jettonTransfer.jetton.address, wallet.accountId)
                .value(amount)
                .to(currency)


            return HistoryItem.Event(
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
                currency = CurrencyFormatter.format(currency.code, inCurrency),
            )
        } else if (action.tonTransfer != null) {
            val tonTransfer = action.tonTransfer!!

            val isOut = !wallet.isMyAddress(tonTransfer.recipient.address)

            val itemAction: ActionType
            val accountAddress: AccountAddress

            val amount = Coin.toCoins(tonTransfer.amount)
            var value = CurrencyFormatter.format(SupportedCurrency.TON.code, amount, amountModifier)

            if (isOut) {
                itemAction = ActionType.Send
                accountAddress = tonTransfer.recipient
                value = withMinusPrefix(value)
            } else {
                itemAction = ActionType.Received
                accountAddress = tonTransfer.sender
                value = withPlusPrefix(value)
            }

            val inCurrency = from(SupportedTokens.TON, wallet.accountId)
                .value(tonTransfer.amount)
                .to(currency)

            return HistoryItem.Event(
                txId = txId,
                iconURL = accountAddress.iconURL,
                action = itemAction,
                title = simplePreview.name,
                subtitle = accountAddress.nameOrAddress,
                comment = tonTransfer.comment,
                value = value,
                tokenCode = SupportedTokens.TON.code,
                coinIconUrl = Global.tonCoinUrl,
                timestamp = timestamp,
                date = date,
                isOut = isOut,
                address = accountAddress.address.toUserFriendly(),
                addressName = accountAddress.name,
                currency = CurrencyFormatter.formatFiat(inCurrency),
            )
        } else if (action.smartContractExec != null) {
            val smartContractExec = action.smartContractExec!!
            val executor = smartContractExec.executor

            val amount = Coin.toCoins(smartContractExec.tonAttached)
            val value = CurrencyFormatter.format(SupportedCurrency.TON.code, amount, amountModifier)

            return HistoryItem.Event(
                txId = txId,
                iconURL = executor.iconURL,
                action = ActionType.CallContract,
                title = simplePreview.name,
                subtitle = executor.nameOrAddress,
                value = withMinusPrefix(value),
                tokenCode = SupportedTokens.TON.code,
                timestamp = timestamp,
                date = date,
                isOut = true,
            )
        } else if (action.nftItemTransfer != null) {
            val nftItemTransfer = action.nftItemTransfer!!
            val isOut = !wallet.isMyAddress(nftItemTransfer.recipient?.address ?: "-")

            val itemAction: ActionType
            val iconURL: String?
            val subtitle: String

            if (isOut) {
                itemAction = ActionType.NftSend
                iconURL = nftItemTransfer.recipient?.iconURL
                subtitle = nftItemTransfer.recipient?.nameOrAddress ?: ""
            } else {
                itemAction = ActionType.NftReceived
                iconURL = nftItemTransfer.sender?.iconURL
                subtitle = nftItemTransfer.sender?.nameOrAddress ?: ""
            }

            val nftItem = nftRepository.getItem(nftItemTransfer.nft)

            return HistoryItem.Event(
                txId = txId,
                iconURL = iconURL,
                action = itemAction,
                title = simplePreview.name,
                subtitle = subtitle,
                value = "NFT",
                nftImageURL = nftItem?.imageURL,
                nftTitle = nftItem?.title,
                nftCollection = nftItem?.description,
                nftAddress = nftItem?.address,
                tokenCode = "NFT",
                timestamp = timestamp,
                date = date,
                isOut = isOut,
            )
        } else if (action.contractDeploy != null) {
            return HistoryItem.Event(
                txId = txId,
                iconURL = "",
                action = ActionType.DeployContract,
                title = simplePreview.name,
                subtitle = wallet.address.shortAddress,
                value = MINUS_SYMBOL,
                tokenCode = SupportedTokens.TON.code,
                timestamp = timestamp,
                date = date,
                isOut = false,
            )
        } else if (action.depositStake != null) {
            val depositStake = action.depositStake!!

            val amount = Coin.toCoins(depositStake.amount)
            val value = CurrencyFormatter.format(SupportedCurrency.TON.code, amount, amountModifier)

            return HistoryItem.Event(
                iconURL = depositStake.implementation.iconURL,
                txId = txId,
                action = ActionType.DepositStake,
                title = simplePreview.name,
                subtitle = depositStake.pool.nameOrAddress,
                value = withMinusPrefix(value),
                tokenCode = SupportedTokens.TON.code,
                timestamp = timestamp,
                coinIconUrl = depositStake.implementation.iconURL,
                date = date,
                isOut = false,
            )
        } else if (action.jettonMint != null) {
            val jettonMint = action.jettonMint!!

            val amount = jettonMint.parsedAmount

            val value = CurrencyFormatter.format(jettonMint.jetton.symbol, amount, amountModifier)

            return HistoryItem.Event(
                txId = txId,
                action = ActionType.JettonMint,
                title = simplePreview.name,
                subtitle = jettonMint.jetton.name,
                value = withPlusPrefix(value),
                tokenCode = jettonMint.jetton.symbol,
                timestamp = timestamp,
                coinIconUrl = jettonMint.jetton.image,
                date = date,
                isOut = false,
            )
        } else if (action.withdrawStakeRequest != null) {
            val withdrawStakeRequest = action.withdrawStakeRequest!!

            val amount = Coin.toCoins(withdrawStakeRequest.amount ?: 0L)
            val value = CurrencyFormatter.format(SupportedCurrency.TON.code, amount, amountModifier)

            return HistoryItem.Event(
                iconURL = withdrawStakeRequest.implementation.iconURL,
                txId = txId,
                action = ActionType.WithdrawStakeRequest,
                title = simplePreview.name,
                subtitle = withdrawStakeRequest.pool.nameOrAddress,
                value = value,
                tokenCode = SupportedTokens.TON.code,
                timestamp = timestamp,
                coinIconUrl = withdrawStakeRequest.implementation.iconURL,
                date = date,
                isOut = false,
            )
        } else if (action.domainRenew != null) {
            val domainRenew = action.domainRenew!!

            return HistoryItem.Event(
                txId = txId,
                action = ActionType.DomainRenewal,
                title = simplePreview.name,
                subtitle = domainRenew.domain,
                value = MINUS_SYMBOL,
                tokenCode = "",
                timestamp = timestamp,
                date = date,
                isOut = false,
            )
        } else if (action.auctionBid != null) {
            val auctionBid = action.auctionBid!!
            val subtitle = auctionBid.nft?.title ?: auctionBid.bidder.nameOrAddress

            val amount = Coin.toCoins(auctionBid.amount.value.toLong())
            val tokenCode = auctionBid.amount.tokenName

            val value = CurrencyFormatter.format(auctionBid.amount.tokenName, amount, amountModifier)

            return HistoryItem.Event(
                txId = txId,
                action = ActionType.AuctionBid,
                title = simplePreview.name,
                subtitle = subtitle,
                value = withMinusPrefix(value),
                tokenCode = tokenCode,
                timestamp = timestamp,
                date = date,
                isOut = false,
            )
        } else if (action.type == Action.Type.unknown) {
            return createUnknown(txId, action, date, timestamp, simplePreview)
        } else if (action.withdrawStake != null) {
            val withdrawStake = action.withdrawStake!!

            val amount = Coin.toCoins(withdrawStake.amount)
            val value = CurrencyFormatter.format(SupportedCurrency.TON.code, amount, amountModifier)

            return HistoryItem.Event(
                txId = txId,
                iconURL = withdrawStake.implementation.iconURL,
                action = ActionType.WithdrawStake,
                title = simplePreview.name,
                subtitle = withdrawStake.pool.nameOrAddress,
                value = withPlusPrefix(value),
                tokenCode = SupportedTokens.TON.code,
                timestamp = timestamp,
                coinIconUrl = withdrawStake.implementation.iconURL,
                date = date,
                isOut = false,
            )
        } else if (action.nftPurchase != null) {
            val nftPurchase = action.nftPurchase!!

            val amount = Coin.toCoins(nftPurchase.amount.value.toLong())
            val value = CurrencyFormatter.format(nftPurchase.amount.tokenName, amount, amountModifier)
            val nftItem = nftPurchase.nft

            return HistoryItem.Event(
                txId = txId,
                action = ActionType.NftPurchase,
                title = simplePreview.name,
                subtitle = nftPurchase.buyer.nameOrAddress,
                value = withMinusPrefix(value),
                tokenCode = SupportedTokens.TON.code,
                timestamp = timestamp,
                nftImageURL = nftItem.imageURL,
                nftTitle = nftItem.title,
                nftCollection = nftItem.description,
                nftAddress = nftItem.address,
                date = date,
                isOut = false,
            )
        } else if (action.jettonBurn != null) {
            val jettonBurn = action.jettonBurn!!

            val amount = jettonBurn.parsedAmount
            val value = CurrencyFormatter.format(jettonBurn.jetton.symbol, amount, amountModifier)

            return HistoryItem.Event(
                txId = txId,
                action = ActionType.JettonBurn,
                title = simplePreview.name,
                subtitle = jettonBurn.sender.nameOrAddress,
                value = withMinusPrefix(value),
                tokenCode = jettonBurn.jetton.symbol,
                timestamp = timestamp,
                coinIconUrl = jettonBurn.jetton.image,
                date = date,
                isOut = false,
            )
        } else if (action.unSubscribe != null) {
            val unsubscribe = action.unSubscribe!!

            return HistoryItem.Event(
                txId = txId,
                action = ActionType.UnSubscribe,
                title = simplePreview.name,
                subtitle = unsubscribe.beneficiary.nameOrAddress,
                value = MINUS_SYMBOL,
                tokenCode = SupportedTokens.TON.code,
                timestamp = timestamp,
                coinIconUrl = unsubscribe.beneficiary.iconURL ?: "",
                date = date,
                isOut = false,
            )
        } else if (action.subscribe != null) {
            val subscribe = action.subscribe!!

            val amount = Coin.toCoins(subscribe.amount)
            val value = CurrencyFormatter.format(SupportedCurrency.TON.code, amount, amountModifier)

            return HistoryItem.Event(
                txId = txId,
                action = ActionType.Subscribe,
                title = simplePreview.name,
                subtitle = subscribe.beneficiary.nameOrAddress,
                value = withMinusPrefix(value),
                tokenCode = SupportedTokens.TON.code,
                timestamp = timestamp,
                coinIconUrl = subscribe.beneficiary.iconURL ?: "",
                date = date,
                isOut = false,
            )
        } else {
            return createUnknown(txId, action, date, timestamp, simplePreview)
        }
    }

    private fun withPlusPrefix(value: String): String {
        return "$PLUS_SYMBOL $value"
    }

    private fun withMinusPrefix(value: String): String {
        return "$MINUS_SYMBOL $value"
    }

    private fun createUnknown(
        txId: String,
        action: Action,
        date: String,
        timestamp: Long,
        simplePreview: ActionSimplePreview,
    ) = HistoryItem.Event(
        txId = txId,
        action = ActionType.Unknown,
        title = simplePreview.name,
        subtitle = action.simplePreview.description,
        value = MINUS_SYMBOL,
        tokenCode = SupportedTokens.TON.code,
        timestamp = timestamp,
        date = date,
        isOut = false,
    )
}