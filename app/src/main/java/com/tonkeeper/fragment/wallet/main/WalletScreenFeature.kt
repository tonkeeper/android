package com.tonkeeper.fragment.wallet.main

import android.net.Uri
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.tonkeeper.App
import com.tonkeeper.api.account.AccountRepository
import com.tonkeeper.api.address
import com.tonkeeper.api.jetton.JettonRepository
import com.tonkeeper.api.parsedBalance
import com.tonkeeper.core.Coin
import com.tonkeeper.core.formatter.CurrencyFormatter
import com.tonkeeper.core.currency.CurrencyManager
import com.tonkeeper.core.currency.from
import com.tonkeeper.event.ChangeCurrencyEvent
import com.tonkeeper.event.ChangeWalletNameEvent
import com.tonkeeper.event.WalletStateUpdateEvent
import com.tonkeeper.event.UpdateCurrencyRateEvent
import com.tonkeeper.fragment.wallet.main.list.item.WalletActionItem
import com.tonkeeper.fragment.wallet.main.list.item.WalletDataItem
import com.tonkeeper.fragment.wallet.main.list.item.WalletItem
import com.tonkeeper.fragment.wallet.main.list.item.WalletJettonCellItem
import com.tonkeeper.fragment.wallet.main.list.item.WalletSpaceItem
import com.tonkeeper.fragment.wallet.main.list.item.WalletTonCellItem
import ton.SupportedCurrency
import ton.SupportedTokens
import core.EventBus
import core.QueueScope
import io.tonapi.models.Account
import io.tonapi.models.JettonBalance
import uikit.mvi.AsyncState
import uikit.mvi.UiFeature
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.ton.block.Coins
import ton.extensions.toUserFriendly
import uikit.list.ListCell

class WalletScreenFeature: UiFeature<WalletScreenState, WalletScreenEffect>(WalletScreenState()) {

    private val changeCurrencyAction = fun(_: ChangeCurrencyEvent) {
        queueScope.submit { updateWalletState(false) }
    }

    private val updateCurrencyRateAction = fun(_: UpdateCurrencyRateEvent) {
        queueScope.submit { updateWalletState(false) }
    }

    private val newTransactionItem = fun(_: WalletStateUpdateEvent) {
        queueScope.submit { updateWalletState(true) }
    }

    private val updateWalletNameAction = fun (event: ChangeWalletNameEvent) {
        updateUiState { currentState ->
            currentState.copy(
                title = event.name
            )
        }
    }

    // dirty hack to remove trailing zeros
    private val amountModifier = { value: String ->
        value.removeSuffix(CurrencyFormatter.monetaryDecimalSeparator + "00")
    }

    private val currency: SupportedCurrency
        get() = App.settings.currency

    private val currencyManager = CurrencyManager.getInstance()
    private val queueScope = QueueScope(viewModelScope.coroutineContext)
    private val accountRepository = AccountRepository()
    private val jettonRepository = JettonRepository()

    init {
        requestWalletState()
        EventBus.subscribe(UpdateCurrencyRateEvent::class.java, updateCurrencyRateAction)
        EventBus.subscribe(ChangeCurrencyEvent::class.java, changeCurrencyAction)
        EventBus.subscribe(ChangeWalletNameEvent::class.java, updateWalletNameAction)
        EventBus.subscribe(WalletStateUpdateEvent::class.java, newTransactionItem)
    }

    private fun requestWalletState() {
        queueScope.submit {
            updateUiState { currentState ->
                currentState.copy(
                    asyncState = AsyncState.Loading
                )
            }

            updateWalletState(false)
            updateWalletState(true)
        }
    }

    private suspend fun updateWalletState(
        sync: Boolean,
    ) {
        val wallet = App.walletManager.getWalletInfo() ?: return

        val data = getWalletData(wallet.accountId, sync) ?: return

        val tonInCurrency = from(SupportedTokens.TON, data.accountId)
            .value(data.account.balance)
            .to(currency)

        val tokens = buildTokensList(
            wallet.accountId,
            Coin.toCoins(data.account.balance),
            tonInCurrency,
            data.jettons.sortedByDescending {
                it.parsedBalance
            }
        )

        var allInCurrency = tonInCurrency

        for (jetton in data.jettons) {
            val jettonInCurrency = from(jetton.address, data.accountId)
                .value(jetton.parsedBalance)
                .to(currency)

            allInCurrency += jettonInCurrency
        }

        val asyncState = if (sync) {
            AsyncState.Default
        } else {
            uiState.value.asyncState
        }

        val items = mutableListOf<WalletItem>()
        items.add(WalletDataItem(
            amount = CurrencyFormatter.formatFiat(allInCurrency),
            address = data.accountId.toUserFriendly()
        ))
        items.add(WalletActionItem)
        items.add(WalletSpaceItem)
        items.addAll(tokens)

        updateUiState { currentState ->
            currentState.copy(
                asyncState = asyncState,
                title = wallet.name,
                items = items
            )
        }
    }

    private suspend fun getRate(
        accountId: String,
        token: String
    ): Float {
        return currencyManager.getRate(
            accountId,
            token,
            currency.code
        )
    }

    private suspend fun getRate24h(
        accountId: String,
        token: String
    ): String {
        return currencyManager.getRate24h(
            accountId,
            token,
            currency.code
        )
    }

    private suspend fun buildTokensList(
        accountId: String,
        balance: Float,
        tonInCurrency: Float,
        jettons: List<JettonBalance>
    ): List<WalletItem> {
        val items = mutableListOf<WalletItem>()

        val rate = getRate(
            accountId,
            SupportedTokens.TON.code,
        )

        val rate24h = getRate24h(
            accountId,
            SupportedTokens.TON.code,
        )

        val size = jettons.size + 1

        val tonItem = WalletTonCellItem(
            balance = CurrencyFormatter.format(value = balance, modifier = amountModifier),
            balanceCurrency = CurrencyFormatter.formatFiat(tonInCurrency),
            rate = CurrencyFormatter.formatRate(currency.code, rate),
            rateDiff24h = rate24h,
            position = ListCell.getPosition(size, 0)
        )
        items.add(tonItem)

        for ((index, jetton) in jettons.withIndex()) {
            val cellPosition = ListCell.getPosition(size, index + 1)

            val tokenBalance = jetton.parsedBalance
            val tokenBalanceCurrency = from(jetton.address, accountId)
                .value(tokenBalance)
                .to(currency)

            val hasRate = tokenBalanceCurrency > 0f

            val tokenRate = getRate(
                accountId,
                jetton.address,
            )

            val tokenRate24h = getRate24h(
                accountId,
                jetton.address,
            )

            val balanceCurrency = if (hasRate) {
                CurrencyFormatter.formatFiat(tokenBalanceCurrency)
            } else ""

            val tokenRateFormat = if (hasRate) {
                CurrencyFormatter.formatRate(currency.code, tokenRate)
            } else ""

            val item = WalletJettonCellItem(
                address = jetton.address,
                name = jetton.jetton.name,
                position = cellPosition,
                iconURI = Uri.parse(jetton.jetton.image),
                code = jetton.jetton.symbol,
                balance = CurrencyFormatter.format(value = tokenBalance, modifier = amountModifier),
                balanceCurrency = balanceCurrency,
                rate = tokenRateFormat,
                rateDiff24h = tokenRate24h,
            )
            items.add(item)
        }

        return items
    }

    override fun onCleared() {
        super.onCleared()
        queueScope.cancel()
        EventBus.unsubscribe(ChangeCurrencyEvent::class.java, changeCurrencyAction)
        EventBus.unsubscribe(UpdateCurrencyRateEvent::class.java, updateCurrencyRateAction)
        EventBus.unsubscribe(ChangeWalletNameEvent::class.java, updateWalletNameAction)
        EventBus.unsubscribe(WalletStateUpdateEvent::class.java, newTransactionItem)
    }

    private suspend fun getWalletData(
        accountId: String,
        forceCloud: Boolean
    ): WalletData? = withContext(Dispatchers.IO) {
        val accountDeferred = async {
            if (forceCloud) {
                accountRepository.getFromCloud(accountId)
            } else {
                accountRepository.get(accountId)
            }
        }
        val jettonsDeferred = async {
            if (forceCloud) {
                jettonRepository.getFromCloud(accountId)
            } else {
                jettonRepository.get(accountId)
            }
        }

        val account = accountDeferred.await() ?: return@withContext null
        val jettons = jettonsDeferred.await() ?: return@withContext null

        if (forceCloud) {
            currencyManager.sync(accountId)
        }

        return@withContext WalletData(
            accountId = accountId,
            account = account.data,
            jettons = jettons.data,
            needSync = false
        )
    }

    private data class WalletData(
        val accountId: String,
        val account: Account,
        val jettons: List<JettonBalance>,
        val needSync: Boolean
    )
}