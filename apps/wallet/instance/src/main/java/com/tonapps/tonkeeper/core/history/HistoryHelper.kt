package com.tonapps.tonkeeper.core.history

import android.icu.text.SimpleDateFormat
import com.tonapps.blockchain.Coin
import com.tonapps.blockchain.ton.extensions.toUserFriendly
import com.tonapps.extensions.withMinus
import com.tonapps.extensions.withPlus
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.wallet.localization.Localization
import com.tonapps.tonkeeper.api.amount
import com.tonapps.tonkeeper.api.description
import com.tonapps.tonkeeper.api.fee
import com.tonapps.tonkeeper.api.getNameOrAddress
import com.tonapps.tonkeeper.api.iconURL
import com.tonapps.tonkeeper.api.imageURL
import com.tonapps.tonkeeper.api.jettonPreview
import com.tonapps.tonkeeper.api.parsedAmount
import com.tonapps.tonkeeper.api.shortAddress
import com.tonapps.tonkeeper.api.title
import com.tonapps.tonkeeper.api.ton
import com.tonapps.tonkeeper.core.currency.currency
import com.tonapps.tonkeeper.core.currency.ton
import com.tonapps.tonkeeper.core.history.list.item.HistoryItem
import com.tonapps.tonkeeper.event.WalletStateUpdateEvent
import com.tonapps.tonkeeper.helper.DateFormat
import core.EventBus
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
import com.tonapps.network.Network
import com.tonapps.tonkeeper.App
import com.tonapps.tonkeeper.api.totalFees
import com.tonapps.tonkeeper.helper.DateHelper
import com.tonapps.wallet.api.entity.TokenEntity
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.account.legacy.WalletLegacy
import com.tonapps.wallet.data.rates.RatesRepository
import com.tonapps.wallet.data.rates.entity.RatesEntity
import io.tonapi.models.MessageConsequences
import java.util.Calendar
import java.util.Date

// TODO request refactoring
object HistoryHelper {

    const val EVENT_LIMIT = 20

    const val MINUS_SYMBOL = "-"
    const val PLUS_SYMBOL = "+"

    private val calendar = Calendar.getInstance()

    data class Details(
        val accountId: String,
        val items: List<HistoryItem>,
        val fee: Float,
        val feeFormat: CharSequence,
        val feeFiat: Float,
        val feeFiatFormat: CharSequence
    )

    private fun createWalletLegacy(wallet: WalletEntity): WalletLegacy {
        return WalletLegacy(
            id = wallet.id,
            name = wallet.label.name,
            publicKey = wallet.publicKey,
            type = wallet.type,
            emoji = wallet.label.emoji,
            color = wallet.label.color,
            version = wallet.version,
            source = wallet.source
        )
    }

    suspend fun create(
        wallet: WalletEntity,
        response: MessageConsequences,
        rates: RatesEntity,
    ): Details {
        val legacy = createWalletLegacy(wallet)
        return create(legacy, response, rates)
    }

    suspend fun mapping(
        wallet: WalletEntity,
        events: List<AccountEvent>,
        removeDate: Boolean = false,
    ): List<HistoryItem> {
        val legacy = createWalletLegacy(wallet)
        return mapping(legacy, events, removeDate)
    }

    suspend fun create(
        wallet: WalletLegacy,
        response: MessageConsequences,
        rates: RatesEntity,
    ): Details {
        val items = mapping(wallet, response.event, true)
        val fee = Coin.toCoins(response.totalFees)
        val feeFormat = CurrencyFormatter.format("TON", fee)
        val feeFiat = rates.convert("TON", fee)
        val feeFiatFormat = CurrencyFormatter.formatFiat(rates.currency.code, feeFiat)
        return Details(
            accountId = wallet.accountId,
            items = items,
            fee = fee,
            feeFormat = feeFormat,
            feeFiat = feeFiat,
            feeFiatFormat = feeFiatFormat
        )
    }

    fun withLoadingItem(items: List<HistoryItem>): List<HistoryItem> {
        val last = items.lastOrNull()
        if (last is HistoryItem.Loader) {
            return items
        }

        val newItems = items.toMutableList()
        newItems.add(HistoryItem.Loader(newItems.size, System.currentTimeMillis() / 1000))
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

    suspend fun mapping(
        wallet: WalletLegacy,
        event: AccountEvent,
        removeDate: Boolean = false,
    ): List<HistoryItem> {
        return mapping(wallet, arrayListOf(event), removeDate)
    }

    suspend fun mapping(
        wallet: WalletLegacy,
        events: AccountEvents,
        removeDate: Boolean = false,
    ): List<HistoryItem> {
        return mapping(wallet, events.events, removeDate)
    }

    fun formatDate(timestamp: Long): String {
        if (timestamp == 0L) {
            return ""
        }
        return DateHelper.formatTime(timestamp)
    }

    suspend fun mapping(
        wallet: WalletLegacy,
        events: List<AccountEvent>,
        removeDate: Boolean = false,
    ): List<HistoryItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<HistoryItem>()

        for ((index, event) in events.withIndex()) {

            val pending = event.inProgress
            val prevEvent = events.getOrNull(index - 1)

            val actions = event.actions
            val fee = event.fee
            val currency = App.settings.currency

            val feeInCurrency = wallet.ton(fee)
                .convert(currency.code)


            val chunkItems = mutableListOf<HistoryItem>()
            for ((actionIndex, action) in actions.withIndex()) {
                val timestamp = if (removeDate) 0 else event.timestamp
                val item = action(event.eventId, wallet, action, timestamp)
                val feeAmount = Coin.toCoins(event.fee)
                chunkItems.add(item.copy(
                    pending = pending,
                    position = com.tonapps.uikit.list.ListCell.getPosition(actions.size, actionIndex),
                    fee = CurrencyFormatter.format("TON", feeAmount),
                    feeInCurrency = CurrencyFormatter.formatFiat(currency.code, feeInCurrency),
                    lt = event.lt,
                ))
            }

            if (chunkItems.size > 0) {
                items.addAll(chunkItems)
            }
        }

        return@withContext items
    }

    private suspend fun action(
        txId: String,
        wallet: WalletLegacy,
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
            val value: CharSequence
            val value2: CharSequence
            val tokenCode: String

            if (!isOut) {
                tokenCode = "TON"
                value = CurrencyFormatter.format("TON", tonFromJetton)
                value2 = CurrencyFormatter.format(symbol, amount)
            } else {
                tokenCode = symbol
                value = CurrencyFormatter.format(symbol, amount)
                value2 = CurrencyFormatter.format("TON", tonFromJetton)
            }

            val inCurrency = wallet.currency(jettonSwap.jettonPreview!!.address)
                .value(jettonSwap.amount.toFloat())
                .convert(currency.code)

            return HistoryItem.Event(
                txId = txId,
                iconURL = "",
                action = ActionType.Swap,
                title = simplePreview.name,
                subtitle = jettonSwap.router.getNameOrAddress(wallet.testnet),
                comment = "",
                value = value.withPlus,
                value2 = value2.withMinus,
                tokenCode = tokenCode,
                coinIconUrl = jettonPreview.image,
                timestamp = timestamp,
                date = date,
                isOut = isOut,
                currency = CurrencyFormatter.formatFiat(currency.code, inCurrency),
            )
        } else if (action.jettonTransfer != null) {
            val jettonTransfer = action.jettonTransfer!!
            val symbol = jettonTransfer.jetton.symbol
            val isOut = !wallet.isMyAddress(jettonTransfer.recipient?.address ?: "")

            val amount = Coin.parseFloat(jettonTransfer.amount, jettonTransfer.jetton.decimals)
            var value = CurrencyFormatter.format(symbol, amount)

            val itemAction: ActionType
            val accountAddress: AccountAddress?

            if (isOut) {
                itemAction = ActionType.Send
                accountAddress = jettonTransfer.recipient
                value = value.withMinus
            } else {
                itemAction = ActionType.Received
                accountAddress = jettonTransfer.sender
                value = value.withPlus
            }

            val inCurrency = wallet.currency(jettonTransfer.jetton.address)
                .value(amount)
                .convert(currency.code)


            return HistoryItem.Event(
                txId = txId,
                iconURL = accountAddress?.iconURL ?: "",
                action = itemAction,
                title = simplePreview.name,
                subtitle = accountAddress?.getNameOrAddress(wallet.testnet) ?: "",
                comment = jettonTransfer.comment,
                value = value,
                tokenCode = "",
                coinIconUrl = jettonTransfer.jetton.image,
                timestamp = timestamp,
                date = date,
                isOut = isOut,
                address = accountAddress?.address?.toUserFriendly(
                    wallet = accountAddress.isWallet,
                    testnet = wallet.testnet
                ),
                addressName = accountAddress?.name,
                currency = CurrencyFormatter.format(currency.code, inCurrency),
            )
        } else if (action.tonTransfer != null) {
            val tonTransfer = action.tonTransfer!!

            val isOut = !wallet.isMyAddress(tonTransfer.recipient.address)

            val itemAction: ActionType
            val accountAddress: AccountAddress

            val amount = Coin.toCoins(tonTransfer.amount)
            var value = CurrencyFormatter.format("TON", amount)

            if (isOut) {
                itemAction = ActionType.Send
                accountAddress = tonTransfer.recipient
                value = value.withMinus
            } else {
                itemAction = ActionType.Received
                accountAddress = tonTransfer.sender
                value = value.withPlus
            }

            val inCurrency = wallet.ton(tonTransfer.amount)
                .convert(currency.code)

            return HistoryItem.Event(
                txId = txId,
                iconURL = accountAddress.iconURL,
                action = itemAction,
                title = simplePreview.name,
                subtitle = accountAddress.getNameOrAddress(wallet.testnet),
                comment = tonTransfer.comment,
                value = value,
                tokenCode = "TON",
                coinIconUrl = TokenEntity.TON.imageUri.toString(),
                timestamp = timestamp,
                date = date,
                isOut = isOut,
                address = accountAddress.address.toUserFriendly(
                    wallet = accountAddress.isWallet,
                    testnet = wallet.testnet
                ),
                addressName = accountAddress.name,
                currency = CurrencyFormatter.formatFiat(currency.code, inCurrency),
            )
        } else if (action.smartContractExec != null) {
            val smartContractExec = action.smartContractExec!!
            val executor = smartContractExec.executor

            val amount = Coin.toCoins(smartContractExec.tonAttached)
            val value = CurrencyFormatter.format("TON", amount)

            return HistoryItem.Event(
                txId = txId,
                iconURL = executor.iconURL,
                action = ActionType.CallContract,
                title = simplePreview.name,
                subtitle = executor.getNameOrAddress(wallet.testnet),
                value = value.withMinus,
                tokenCode = "TON",
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
                subtitle = nftItemTransfer.recipient?.getNameOrAddress(wallet.testnet) ?: ""
            } else {
                itemAction = ActionType.NftReceived
                iconURL = nftItemTransfer.sender?.iconURL
                subtitle = nftItemTransfer.sender?.getNameOrAddress(wallet.testnet) ?: ""
            }

            // val nftItem = nftRepository.getItem(nftItemTransfer.nft, wallet.testnet)

            return HistoryItem.Event(
                txId = txId,
                iconURL = iconURL,
                action = itemAction,
                title = simplePreview.name,
                subtitle = subtitle,
                value = "NFT",
                /*nftImageURL = nftItem?.imageURL,
                nftTitle = nftItem?.title,
                nftCollection = nftItem?.description,
                nftAddress = nftItem?.address,*/
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
                tokenCode = "TON",
                timestamp = timestamp,
                date = date,
                isOut = false,
            )
        } else if (action.depositStake != null) {
            val depositStake = action.depositStake!!

            val amount = Coin.toCoins(depositStake.amount)
            val value = CurrencyFormatter.format("TON", amount)

            return HistoryItem.Event(
                iconURL = depositStake.implementation.iconURL,
                txId = txId,
                action = ActionType.DepositStake,
                title = simplePreview.name,
                subtitle = depositStake.pool.getNameOrAddress(wallet.testnet),
                value = value.withMinus,
                tokenCode = "TON",
                timestamp = timestamp,
                coinIconUrl = depositStake.implementation.iconURL,
                date = date,
                isOut = false,
            )
        } else if (action.jettonMint != null) {
            val jettonMint = action.jettonMint!!

            val amount = jettonMint.parsedAmount

            val value = CurrencyFormatter.format(jettonMint.jetton.symbol, amount)

            return HistoryItem.Event(
                txId = txId,
                action = ActionType.JettonMint,
                title = simplePreview.name,
                subtitle = jettonMint.jetton.name,
                value = value.withPlus,
                tokenCode = jettonMint.jetton.symbol,
                timestamp = timestamp,
                coinIconUrl = jettonMint.jetton.image,
                date = date,
                isOut = false,
            )
        } else if (action.withdrawStakeRequest != null) {
            val withdrawStakeRequest = action.withdrawStakeRequest!!

            val amount = Coin.toCoins(withdrawStakeRequest.amount ?: 0L)
            val value = CurrencyFormatter.format("TON", amount)

            return HistoryItem.Event(
                iconURL = withdrawStakeRequest.implementation.iconURL,
                txId = txId,
                action = ActionType.WithdrawStakeRequest,
                title = simplePreview.name,
                subtitle = withdrawStakeRequest.pool.getNameOrAddress(wallet.testnet),
                value = value,
                tokenCode = "TON",
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
            val subtitle = auctionBid.nft?.title ?: auctionBid.bidder.getNameOrAddress(wallet.testnet)

            val amount = Coin.toCoins(auctionBid.amount.value.toLong())
            val tokenCode = auctionBid.amount.tokenName

            val value = CurrencyFormatter.format(auctionBid.amount.tokenName, amount)

            return HistoryItem.Event(
                txId = txId,
                action = ActionType.AuctionBid,
                title = simplePreview.name,
                subtitle = subtitle,
                value = value.withMinus,
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
            val value = CurrencyFormatter.format("TON", amount)

            return HistoryItem.Event(
                txId = txId,
                iconURL = withdrawStake.implementation.iconURL,
                action = ActionType.WithdrawStake,
                title = simplePreview.name,
                subtitle = withdrawStake.pool.getNameOrAddress(wallet.testnet),
                value = value.withPlus,
                tokenCode = "TON",
                timestamp = timestamp,
                coinIconUrl = withdrawStake.implementation.iconURL,
                date = date,
                isOut = false,
            )
        } else if (action.nftPurchase != null) {
            val nftPurchase = action.nftPurchase!!

            val amount = Coin.toCoins(nftPurchase.amount.value.toLong())
            val value = CurrencyFormatter.format(nftPurchase.amount.tokenName, amount)
            val nftItem = nftPurchase.nft

            return HistoryItem.Event(
                txId = txId,
                action = ActionType.NftPurchase,
                title = simplePreview.name,
                subtitle = nftPurchase.buyer.getNameOrAddress(wallet.testnet),
                value = value.withMinus,
                tokenCode = "TON",
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
            val value = CurrencyFormatter.format(jettonBurn.jetton.symbol, amount)

            return HistoryItem.Event(
                txId = txId,
                action = ActionType.JettonBurn,
                title = simplePreview.name,
                subtitle = jettonBurn.sender.getNameOrAddress(wallet.testnet),
                value = value.withMinus,
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
                subtitle = unsubscribe.beneficiary.getNameOrAddress(wallet.testnet),
                value = MINUS_SYMBOL,
                tokenCode = "TON",
                timestamp = timestamp,
                coinIconUrl = unsubscribe.beneficiary.iconURL ?: "",
                date = date,
                isOut = false,
            )
        } else if (action.subscribe != null) {
            val subscribe = action.subscribe!!

            val amount = Coin.toCoins(subscribe.amount)
            val value = CurrencyFormatter.format("TON", amount)

            return HistoryItem.Event(
                txId = txId,
                action = ActionType.Subscribe,
                title = simplePreview.name,
                subtitle = subscribe.beneficiary.getNameOrAddress(wallet.testnet),
                value = value.withMinus,
                tokenCode = "TON",
                timestamp = timestamp,
                coinIconUrl = subscribe.beneficiary.iconURL ?: "",
                date = date,
                isOut = false,
            )
        } else {
            return createUnknown(txId, action, date, timestamp, simplePreview)
        }
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
        tokenCode = "TON",
        timestamp = timestamp,
        date = date,
        isOut = false,
    )
}