package com.tonapps.tonkeeper.ui.screen.wallet

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.network.NetworkMonitor
import com.tonapps.tonkeeper.ui.screen.wallet.list.Item
import com.tonapps.uikit.list.ListCell
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.entity.TokenEntity
import com.tonapps.wallet.data.account.WalletRepository
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.account.entities.WalletEvent
import com.tonapps.wallet.data.token.entities.AccountTokenEntity
import com.tonapps.wallet.data.core.WalletCurrency
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.token.TokenRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import uikit.extensions.collectFlow

class WalletViewModel(
    private val walletRepository: WalletRepository,
    private val settings: SettingsRepository,
    private val tokenRepository: TokenRepository,
    private val api: API,
    private val networkMonitor: NetworkMonitor
): ViewModel() {

    private data class Tokens(
        val wallet: WalletEntity,
        val currency: WalletCurrency,
        val list: List<AccountTokenEntity>,
        val isOnline: Boolean
    )

    private val _tokensFlow = MutableStateFlow<Tokens?>(null)
    private val tokensFlow = _tokensFlow.asStateFlow().filterNotNull().filter { it.list.isNotEmpty() }

    private val _lastLtFlow = MutableStateFlow<Long>(0)
    private val lastLtFlow = _lastLtFlow.asStateFlow()

    private val _statusFlow = MutableSharedFlow<Item.Status>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val statusFlow = _statusFlow.asSharedFlow()

    private val _uiItemsFlow = MutableStateFlow<List<Item>>(emptyList())
    val uiItemsFlow = _uiItemsFlow.asStateFlow().filter { it.isNotEmpty() }

    val uiLabelFlow = walletRepository.activeWalletFlow.map { it.label }

    init {
        collectFlow(settings.hiddenBalancesFlow.drop(1)) { hiddenBalance ->
            _uiItemsFlow.value = _uiItemsFlow.value.map {
                when (it) {
                    is Item.Balance -> it.copy(hiddenBalance = hiddenBalance)
                    is Item.Token -> it.copy(hiddenBalance = hiddenBalance)
                    else -> it
                }
            }
        }

        collectFlow(walletRepository.realtimeEventsFlow) { event ->
            if (event is WalletEvent.Boc) {
                setStatus(Item.Status.SendingTransaction)
            } else if (event is WalletEvent.Transaction) {
                setStatus(Item.Status.TransactionConfirmed)
                delay(2000)
                setStatus(Item.Status.Default)
                _lastLtFlow.value = event.lt
            }
        }

        combine(
            walletRepository.activeWalletFlow,
            settings.currencyFlow,
            networkMonitor.isOnlineFlow,
            lastLtFlow
        ) { wallet, currency, isOnline, lastLt ->

            if (lastLt == 0L) {
                setStatus(Item.Status.Updating)
                _tokensFlow.value = getLocalTokens(wallet, currency, isOnline)
            }

            if (!isOnline) {
                return@combine null
            }

            getRemoteTokens(wallet, currency)?.let { tokens ->
                setStatus(Item.Status.Default)
                _tokensFlow.value = tokens
            }
        }.launchIn(viewModelScope)

        combine(
            tokensFlow,
            statusFlow,
        ) { tokens, status ->
            val (fiatBalance, uiItems) = buildUiItems(tokens.currency, tokens.wallet.testnet, tokens.list)
            val balanceFormat = if (tokens.wallet.testnet) {
                CurrencyFormatter.formatFiat("TON", fiatBalance)
            } else {
                CurrencyFormatter.formatFiat(tokens.currency.code, fiatBalance)
            }

            val actualStatus = if (tokens.isOnline) {
                status
            } else {
                Item.Status.NoInternet
            }
            setItems(tokens.wallet, balanceFormat, uiItems, actualStatus)
        }.launchIn(viewModelScope)
    }

    private fun setStatus(status: Item.Status) {
        _statusFlow.tryEmit(status)
    }

    fun toggleBalance() {
        settings.hiddenBalances = !settings.hiddenBalances
    }

    private suspend fun getLocalTokens(
        wallet: WalletEntity,
        currency: WalletCurrency,
        isOnline: Boolean
    ): Tokens? = withContext(Dispatchers.IO) {
        val tokens = tokenRepository.getLocal(currency, wallet.accountId, wallet.testnet)
        if (tokens.isEmpty()) {
            return@withContext null
        }
        Tokens(wallet, currency, tokens, isOnline)
    }

    private suspend fun getRemoteTokens(
        wallet: WalletEntity,
        currency: WalletCurrency
    ): Tokens? = withContext(Dispatchers.IO) {
        try {
            val tokens = tokenRepository.getRemote(currency, wallet.accountId, wallet.testnet)
            Tokens(wallet, currency, tokens, true)
        } catch (e: Throwable) {
            null
        }
    }

    private fun buildUiItems(
        currency: WalletCurrency,
        testnet: Boolean,
        tokens: List<AccountTokenEntity>,
    ): Pair<Float, List<Item.Token>> {
        var fiatBalance = 0f
        if (testnet) {
            fiatBalance = tokens.first().balance.value
        }
        val uiItems = mutableListOf<Item.Token>()
        for ((index, token) in tokens.withIndex()) {
            fiatBalance += token.fiat

            val balanceFormat = CurrencyFormatter.format(value = token.balance.value)
            val fiatFormat = CurrencyFormatter.formatFiat(currency.code, token.fiat)

            val item = Item.Token(
                position = ListCell.getPosition(tokens.size, index),
                iconUri = token.imageUri,
                address = token.address,
                symbol = token.symbol,
                name = token.name,
                balance = token.balance.value,
                balanceFormat = balanceFormat,
                fiat = token.fiat,
                fiatFormat = fiatFormat,
                rate = CurrencyFormatter.formatFiat(currency.code, token.rateNow),
                rateDiff24h = token.rateDiff24h,
                verified = token.verified,
                testnet = testnet,
                hiddenBalance = settings.hiddenBalances
            )
            uiItems.add(item)
        }
        return Pair(fiatBalance, uiItems)
    }

    private fun setItems(
        wallet: WalletEntity,
        balance: CharSequence,
        list: List<Item.Token>,
        status: Item.Status
    ) {
        val items = mutableListOf<Item>()
        items.add(Item.Balance(
            balance = balance,
            address = wallet.address,
            walletType = wallet.type,
            status = status,
            hiddenBalance = settings.hiddenBalances
        ))
        items.add(Item.Actions(
            address = wallet.address,
            token = TokenEntity.TON,
            walletType = wallet.type,
            swapUri = api.config.swapUri,
        ))
        items.add(Item.Space)
        items.addAll(list)
        items.add(Item.Space)
        _uiItemsFlow.value = items
    }
}