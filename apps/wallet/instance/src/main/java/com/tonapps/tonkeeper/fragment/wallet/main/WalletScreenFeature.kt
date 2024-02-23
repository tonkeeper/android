package com.tonapps.tonkeeper.fragment.wallet.main

import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.tonkeeper.App
import com.tonapps.tonkeeper.api.account.AccountRepository
import com.tonapps.tonkeeper.api.getAddress
import com.tonapps.tonkeeper.api.jetton.JettonRepository
import com.tonapps.tonkeeper.api.parsedBalance
import com.tonapps.tonkeeper.core.Coin
import com.tonapps.tonkeeper.core.currency.CurrencyManager
import com.tonapps.tonkeeper.core.currency.currency
import com.tonapps.tonkeeper.core.currency.ton
import com.tonapps.tonkeeper.event.ChangeCurrencyEvent
import com.tonapps.tonkeeper.event.ChangeWalletLabelEvent
import com.tonapps.tonkeeper.event.WalletStateUpdateEvent
import com.tonapps.tonkeeper.event.UpdateCurrencyRateEvent
import com.tonapps.tonkeeper.extensions.label
import com.tonapps.tonkeeper.fragment.wallet.main.list.item.WalletActionItem
import com.tonapps.tonkeeper.fragment.wallet.main.list.item.WalletBannerItem
import com.tonapps.tonkeeper.fragment.wallet.main.list.item.WalletDataItem
import com.tonapps.tonkeeper.fragment.wallet.main.list.item.WalletItem
import com.tonapps.tonkeeper.fragment.wallet.main.list.item.WalletJettonCellItem
import com.tonapps.tonkeeper.fragment.wallet.main.list.item.WalletSpaceItem
import com.tonapps.tonkeeper.fragment.wallet.main.list.item.WalletTonCellItem
import com.tonapps.wallet.data.core.Currency
import core.EventBus
import core.QueueScope
import io.tonapi.models.Account
import io.tonapi.models.JettonBalance
import uikit.mvi.AsyncState
import uikit.mvi.UiFeature
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ton.extensions.toUserFriendly
import ton.wallet.Wallet
import ton.wallet.WalletManager

class WalletScreenFeature(
    private val walletManager: WalletManager
): UiFeature<WalletScreenState, WalletScreenEffect>(WalletScreenState()) {

    private val changeCurrencyAction = fun(_: ChangeCurrencyEvent) {
        queueScope.submit { updateWalletState(false) }
    }

    private val updateCurrencyRateAction = fun(_: UpdateCurrencyRateEvent) {
        queueScope.submit { updateWalletState(false) }
    }

    private val newTransactionItem = fun(_: WalletStateUpdateEvent) {
        queueScope.submit { updateWalletState(true) }
    }

    private val updateWalletNameAction = fun (event: ChangeWalletLabelEvent) {
        queueScope.submit { updateWalletState(false) }
    }

    private val currency: Currency
        get() = App.settings.currency

    private val currencyManager = CurrencyManager.getInstance()
    private val queueScope = QueueScope(viewModelScope.coroutineContext)
    private val accountRepository = AccountRepository()
    private val jettonRepository = JettonRepository()

    init {
        viewModelScope.launch {
            val wallet = walletManager.getWalletInfo() ?: return@launch
            updateUiState { currentState ->
                currentState.copy(
                    walletLabel = wallet.label
                )
            }
        }

        requestWalletState()
        EventBus.subscribe(UpdateCurrencyRateEvent::class.java, updateCurrencyRateAction)
        EventBus.subscribe(ChangeCurrencyEvent::class.java, changeCurrencyAction)
        EventBus.subscribe(ChangeWalletLabelEvent::class.java, updateWalletNameAction)
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
        val wallet = walletManager.getWalletInfo() ?: return

        val data = getWalletData(wallet.accountId, wallet.testnet, sync) ?: return

        val tonInCurrency = wallet.ton(data.account.balance)
            .convert(currency.code)

        val tokens = buildTokensList(
            wallet,
            Coin.toCoins(data.account.balance),
            tonInCurrency,
            data.jettons.sortedByDescending {
                it.parsedBalance
            }
        )

        var allInCurrency = tonInCurrency

        for (jetton in data.jettons) {
            val jettonAddress = jetton.getAddress(testnet = wallet.testnet)
            val jettonInCurrency = wallet.currency(jettonAddress)
                .value(jetton.parsedBalance)
                .convert(currency.code)

            allInCurrency += jettonInCurrency
        }

        val asyncState = if (sync) {
            AsyncState.Default
        } else {
            uiState.value.asyncState
        }

        val items = mutableListOf<WalletItem>()
        items.add(WalletDataItem(
            amount = CurrencyFormatter.formatFiat(currency.code, allInCurrency),
            address = data.accountId.toUserFriendly(testnet = wallet.testnet),
            walletType = wallet.type
        ))
        items.add(WalletActionItem(wallet.type))
        items.add(WalletSpaceItem)

        if (!App.instance.isOriginalAppInstalled()) {
            items.add(WalletBannerItem)
            items.add(WalletSpaceItem)
        }

        items.addAll(tokens)

        updateUiState { currentState ->
            currentState.copy(
                walletType = wallet.type,
                asyncState = asyncState,
                walletLabel = wallet.label,
                items = items
            )
        }
    }

    private suspend fun getRate(
        wallet: Wallet,
        token: String
    ): Float {
        val accountId = wallet.accountId
        val testnet = wallet.testnet
        return currencyManager.getRate(
            accountId,
            testnet,
            token,
            currency.code
        )
    }

    private suspend fun getRate24h(
        wallet: Wallet,
        token: String
    ): String {
        val accountId = wallet.accountId
        val testnet = wallet.testnet
        return currencyManager.getRate24h(
            accountId,
            testnet,
            token,
            currency.code
        )
    }

    private suspend fun buildTokensList(
        wallet: Wallet,
        balance: Float,
        tonInCurrency: Float,
        jettons: List<JettonBalance>
    ): List<WalletItem> {
        val items = mutableListOf<WalletItem>()

        val rate = getRate(wallet, "TON")

        val rate24h = getRate24h(wallet, "TON")

        val size = jettons.size + 1

        val tonItem = WalletTonCellItem(
            balance = CurrencyFormatter.format(value = balance),
            balanceCurrency = CurrencyFormatter.formatFiat(currency.code, tonInCurrency),
            rate = CurrencyFormatter.formatRate(currency.code, rate),
            rateDiff24h = rate24h,
            position = com.tonapps.uikit.list.ListCell.getPosition(size, 0)
        )
        items.add(tonItem)

        val jettonItems = mutableListOf<WalletJettonCellItem>()

        for ((index, jetton) in jettons.withIndex()) {
            val cellPosition = com.tonapps.uikit.list.ListCell.getPosition(size, index + 1)

            val tokenBalance = jetton.parsedBalance
            val jettonAddress = jetton.getAddress(wallet.testnet)
            val tokenBalanceCurrency = wallet.currency(jettonAddress)
                .value(tokenBalance)
                .convert(currency.code)

            val hasRate = tokenBalanceCurrency > 0f

            val tokenRate = getRate(wallet, jettonAddress)

            val tokenRate24h = getRate24h(wallet, jettonAddress)

            val balanceCurrencyFormat = CurrencyFormatter.formatFiat(currency.code, tokenBalanceCurrency)

            val tokenRateFormat = if (hasRate) {
                CurrencyFormatter.formatRate(currency.code, tokenRate)
            } else ""

            val item = WalletJettonCellItem(
                address = jettonAddress,
                name = jetton.jetton.name,
                position = cellPosition,
                iconURI = Uri.parse(jetton.jetton.image),
                code = jetton.jetton.symbol,
                balance = CurrencyFormatter.format(value = tokenBalance),
                balanceCurrencyFormat = balanceCurrencyFormat,
                balanceCurrency = tokenBalanceCurrency,
                rate = tokenRateFormat,
                rateDiff24h = tokenRate24h,
            )
            jettonItems.add(item)
        }

        jettonItems.sortByDescending { it.balanceCurrency }
        items.addAll(jettonItems)

        return items
    }

    override fun onCleared() {
        super.onCleared()
        queueScope.cancel()
        EventBus.unsubscribe(ChangeCurrencyEvent::class.java, changeCurrencyAction)
        EventBus.unsubscribe(UpdateCurrencyRateEvent::class.java, updateCurrencyRateAction)
        EventBus.unsubscribe(ChangeWalletLabelEvent::class.java, updateWalletNameAction)
        EventBus.unsubscribe(WalletStateUpdateEvent::class.java, newTransactionItem)
    }

    private suspend fun getWalletData(
        accountId: String,
        testnet: Boolean,
        forceCloud: Boolean
    ): WalletData? = withContext(Dispatchers.IO) {
        val accountDeferred = async {
            if (forceCloud) {
                accountRepository.getFromCloud(accountId, testnet)
            } else {
                accountRepository.get(accountId, testnet)
            }
        }
        val jettonsDeferred = async {
            if (forceCloud) {
                jettonRepository.getFromCloud(accountId, testnet)
            } else {
                jettonRepository.get(accountId, testnet)
            }
        }

        val account = accountDeferred.await() ?: return@withContext null
        val jettons = jettonsDeferred.await() ?: return@withContext null

        if (forceCloud) {
            currencyManager.sync(accountId, testnet)
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