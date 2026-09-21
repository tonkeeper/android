package com.tonapps.migration.data

import com.tonapps.blockchain.contract.Blockchain
import com.tonapps.blockchain.model.legacy.BlockchainAddress
import com.tonapps.blockchain.model.legacy.TokenEntity
import com.tonapps.blockchain.model.legacy.WalletCurrency
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.core.extensions.fiatRate
import com.tonapps.core.extensions.formatFiat
import com.tonapps.icu.Coins
import com.tonapps.wallet.api.entity.value.Timestamp
import com.tonapps.wallet.data.events.ActionType
import com.tonapps.wallet.data.events.EventsRepository
import com.tonapps.wallet.data.events.tx.model.TxAction
import com.tonapps.wallet.data.events.tx.model.TxActionBody
import com.tonapps.wallet.data.events.tx.model.TxEvent
import com.tonapps.wallet.data.rates.RatesRepository
import com.tonapps.wallet.features.events.TxEventUiMapper
import io.tonapi.models.MigrationTransaction
import kotlinx.collections.immutable.toImmutableList
import com.tonapps.wallet.features.events.components.legacy.UiEvent
import ui.uiPosition

class MigrationEmulationMapper(
    private val eventsRepository: EventsRepository,
    private val txEventUiMapper: TxEventUiMapper,
    private val ratesRepository: RatesRepository,
) {

    suspend fun mapTransactions(
        sourceWallet: WalletEntity,
        transactions: List<MigrationTransaction>,
        currency: WalletCurrency,
    ): List<UiEvent.Item> {
        if (transactions.isEmpty()) return emptyList()

        val address = BlockchainAddress(
            value = sourceWallet.address,
            network = sourceWallet.network,
            blockchain = Blockchain.TON,
        )
        val txEvents = eventsRepository.mapAccountEventsToTxEvents(
            address,
            transactions.map { it.emulation.event },
        )
        return txEvents.map { txEvent ->
            toUiItem(txEvent, sourceWallet, currency)
        }
    }

    suspend fun mapTronPrepare(
        sourceWallet: WalletEntity,
        tronPrepare: MigrationTronPrepare,
        currency: WalletCurrency,
    ): List<UiEvent.Item> {
        if (!tronPrepare.hasTransfers) return emptyList()

        val address = BlockchainAddress(
            value = tronPrepare.fromAddress,
            network = sourceWallet.network,
            blockchain = Blockchain.TRON,
        )
        val events = buildList {
            if (tronPrepare.usdtAmount.isPositive) {
                add(
                    tronSendEvent(
                        address = address,
                        amount = tronPrepare.usdtAmount,
                        currency = WalletCurrency.USDT_TRON,
                        toAddress = tronPrepare.toAddress,
                        hash = "migration-tron-usdt",
                        imageUrl = TokenEntity.TRON_USDT.imageUri.toString(),
                        batteryCharges = (tronPrepare.fee as? MigrationTronFee.Battery)?.charges,
                    )
                )
            }
            if (tronPrepare.trxAmount.isPositive) {
                add(
                    tronSendEvent(
                        address = address,
                        amount = tronPrepare.trxAmount,
                        currency = TRX_CURRENCY,
                        toAddress = tronPrepare.toAddress,
                        hash = "migration-tron-trx",
                        imageUrl = TokenEntity.TRX.imageUri.toString(),
                        batteryCharges = null,
                    )
                )
            }
        }

        return events.map { txEvent ->
            toUiItem(txEvent, sourceWallet, currency)
        }
    }

    private suspend fun toUiItem(
        txEvent: TxEvent,
        sourceWallet: WalletEntity,
        currency: WalletCurrency,
    ): UiEvent.Item {
        val uiItem = txEventUiMapper.toUiItem(txEvent, sourceWallet)
        val actions = uiItem.actions.zip(txEvent.actions).map { (uiAction, txAction) ->
            uiAction.copy(date = formatActionFiat(txAction, sourceWallet, currency))
        }
        return uiItem.copy(actions = actions.toImmutableList())
    }

    private fun tronSendEvent(
        address: BlockchainAddress,
        amount: Coins,
        currency: WalletCurrency,
        toAddress: String,
        hash: String,
        imageUrl: String,
        batteryCharges: Int?,
    ): TxEvent {
        val isTestnet = address.network.isTestnet
        val body = TxActionBody.Builder(ActionType.Send)
            .setSender(
                TxActionBody.Account(
                    address = address.value,
                    testnet = isTestnet,
                )
            )
            .setRecipient(
                TxActionBody.Account(
                    address = toAddress,
                    testnet = isTestnet,
                )
            )
            .setOutgoingAmount(amount, currency)
            .setImageUrl(imageUrl)
            .build()

        return TxEvent(
            hash = hash,
            lt = 0L,
            timestamp = Timestamp.from(System.currentTimeMillis()),
            actions = listOf(
                TxAction(
                    body = body,
                    status = TxAction.Status.Ok,
                    isMaybeSpam = false,
                )
            ),
            isScam = false,
            inProgress = false,
            progress = 0f,
            blockchain = Blockchain.TRON,
            extra = batteryCharges?.let { TxEvent.Extra.Battery(it) } ?: TxEvent.Extra.Battery(),
        )
    }

    private suspend fun formatActionFiat(
        action: TxAction,
        wallet: WalletEntity,
        currency: WalletCurrency,
    ): String {
        if (action.type == ActionType.NftSend ||
            action.type == ActionType.NftReceived ||
            action.type == ActionType.NftPurchase
        ) {
            return ""
        }
        val amountValue = action.amount.outgoing ?: action.amount.incoming ?: return ""
        val tokenAddress = when {
            amountValue.currency == WalletCurrency.USDT_TRON -> TokenEntity.TRON_USDT.address
            amountValue.currency.address.equals(TokenEntity.TRX.address, ignoreCase = true) ->
                TokenEntity.TRX.address
            else -> amountValue.currency.address
        }
        val rates = ratesRepository.getRates(wallet.network, currency, tokenAddress)
        val rate = rates.rateValue(tokenAddress)
        if (!rate.isPositive) return ""
        return amountValue.value.formatFiat(fiatRate(currency.code, rate))
    }

    companion object {

        fun List<UiEvent.Item>.asContinuousBundle(): List<UiEvent.Item> {
            val totalActions = sumOf { it.actions.size }
            if (totalActions <= 1) return this
            var index = 0
            return map { item ->
                val actions = item.actions.map { action ->
                    val position = uiPosition(index, totalActions)
                    index += 1
                    action.copy(position = position)
                }.toImmutableList()
                item.copy(actions = actions)
            }
        }

        private val TRX_CURRENCY = WalletCurrency(
            code = "TRX",
            title = "TRX",
            alias = "TRON_TRX",
            chain = WalletCurrency.Chain.TRON(
                address = TokenEntity.TRX.address,
                decimals = TokenEntity.TRX.decimals,
            ),
            isToken = false,
        )
    }
}
