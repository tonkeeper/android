package com.tonapps.tonkeeper.ui.screen.wallet

import android.util.Log
import androidx.lifecycle.ViewModel
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.tonkeeper.ui.screen.wallet.list.Item
import com.tonapps.uikit.list.ListCell
import com.tonapps.wallet.api.entity.TokenEntity
import com.tonapps.wallet.data.account.WalletRepository
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.token.entities.AccountTokenEntity
import com.tonapps.wallet.data.core.WalletCurrency
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.token.TokenRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import uikit.extensions.collectFlow

class WalletViewModel(
    private val walletRepository: WalletRepository,
    private val settings: SettingsRepository,
    private val tokenRepository: TokenRepository
): ViewModel() {

    private data class Data(
        val wallet: WalletEntity? = null,
        val currency: WalletCurrency? = null,
    )

    private data class Tokens(
        val wallet: WalletEntity,
        val currency: WalletCurrency,
        val list: List<AccountTokenEntity>,
        val status: Item.Status
    )

    private val _dataFlow = MutableStateFlow(Data())
    private val dataFlow = _dataFlow.asStateFlow().filter { it.wallet != null && it.currency != null }

    private val _tokensFlow = MutableStateFlow<Tokens?>(null)
    private val tokensFlow = _tokensFlow.asStateFlow().filterNotNull().filter { it.list.isNotEmpty() }

    private val _uiItemsFlow = MutableStateFlow<List<Item>>(emptyList())
    val uiItemsFlow = _uiItemsFlow.asStateFlow().filter { it.isNotEmpty() }

    val uiLabelFlow = dataFlow.map { it.wallet }.filterNotNull().map { it.label }

    init {
        collectFlow(walletRepository.activeWalletFlow) { _dataFlow.value = _dataFlow.value.copy(wallet = it) }
        collectFlow(settings.currencyFlow) { _dataFlow.value = _dataFlow.value.copy(currency = it) }

        collectFlow(dataFlow) {
            _tokensFlow.value = getLocalTokens(it)
            _tokensFlow.value = getRemoteTokens(it)
        }

        collectFlow(tokensFlow) {
            val (fiatBalance, uiItems) = buildUiItems(it.currency, it.wallet.testnet, it.list)
            val balanceFormat = if (it.wallet.testnet) {
                CurrencyFormatter.formatFiat("TON", fiatBalance)
            } else {
                CurrencyFormatter.formatFiat(it.currency.code, fiatBalance)
            }
            setItems(it.wallet, balanceFormat, it.status, uiItems)
        }
    }

    private suspend fun getLocalTokens(
        data: Data
    ): Tokens? = withContext(Dispatchers.IO) {
        val currency = data.currency ?: return@withContext null
        val wallet = data.wallet ?: return@withContext null
        val tokens = tokenRepository.getLocal(currency, wallet.accountId, wallet.testnet)
        Tokens(wallet, currency, tokens, Item.Status.Updating)
    }

    private suspend fun getRemoteTokens(
        data: Data
    ): Tokens? = withContext(Dispatchers.IO) {
        val currency = data.currency ?: return@withContext null
        val wallet = data.wallet ?: return@withContext null
        val tokens = tokenRepository.getRemote(currency, wallet.accountId, wallet.testnet)
        Tokens(wallet, currency, tokens, Item.Status.Default)
    }

    private fun buildUiItems(
        currency: WalletCurrency,
        testnet: Boolean,
        tokens: List<AccountTokenEntity>
    ): Pair<Float, List<Item.Token>> {
        var fiatBalance = 0f
        if (testnet) {
            fiatBalance = tokens.first().balance.value
        }
        val uiItems = mutableListOf<Item.Token>()
        for ((index, token) in tokens.withIndex()) {
            fiatBalance += token.fiat
            val item = Item.Token(
                position = ListCell.getPosition(tokens.size, index),
                iconUri = token.imageUri,
                address = token.address,
                symbol = token.symbol,
                name = token.name,
                balance = token.balance.value,
                balanceFormat = CurrencyFormatter.format(value = token.balance.value),
                fiat = token.fiat,
                fiatFormat = CurrencyFormatter.formatFiat(currency.code, token.fiat),
                rate = CurrencyFormatter.formatFiat(currency.code, token.rateNow),
                rateDiff24h = token.rateDiff24h,
                verified = token.verified,
                testnet = testnet
            )
            uiItems.add(item)
        }
        return Pair(fiatBalance, uiItems)
    }

    private fun setItems(
        wallet: WalletEntity,
        balance: String,
        status: Item.Status,
        list: List<Item.Token>,
    ) {
        val items = mutableListOf<Item>()
        items.add(Item.Balance(
            balance = balance,
            address = wallet.address,
            walletType = wallet.type,
            status = status
        ))
        items.add(Item.Actions(
            address = wallet.address,
            token = TokenEntity.TON,
            walletType = wallet.type
        ))
        items.add(Item.Space)
        items.addAll(list)
        items.add(Item.Space)
        _uiItemsFlow.value = items
    }
}