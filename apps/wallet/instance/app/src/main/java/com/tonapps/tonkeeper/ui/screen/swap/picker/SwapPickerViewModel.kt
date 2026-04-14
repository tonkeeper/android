package com.tonapps.tonkeeper.ui.screen.swap.picker

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.tonapps.blockchain.ton.extensions.equalsAddress
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.ui.screen.swap.picker.list.Item
import com.tonapps.uikit.list.ListCell
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.swap.SwapRepository
import com.tonapps.wallet.data.token.TokenRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class SwapPickerViewModel(
    app: Application,
    private val args: SwapPickerArgs,
    private val wallet: WalletEntity,
    private val swapRepository: SwapRepository,
    private val tokenRepository: TokenRepository,
    private val settingsRepository: SettingsRepository
): BaseWalletVM(app) {

    private val _searchQueryFlow = MutableStateFlow("")
    val searchQueryFlow = _searchQueryFlow.asSharedFlow().map { it.trim() }

    val uiItemsFlow = combine(
        swapRepository.assetsFlow,
        searchQueryFlow
    ) { currencies, query ->
        currencies.filter { it.containsQuery(query) }
    }.map { currencies ->
        val items = mutableListOf<Item>()
        val userCurrency = settingsRepository.currency
        if (args.send) {
            val supportedAddresses = currencies.map { it.address }
            val tokens = getTokens().filter { supportedAddresses.contains(it.address) }
            for ((index, token) in tokens.withIndex()) {
                val currency = currencies.find { it.address.equalsAddress(token.address) }!!
                val item = Item.Token(
                    position = ListCell.getPosition(currencies.size, index),
                    currency = currency,
                    selected = currency == args.selectedCurrency,
                    fiatFormatted = CurrencyFormatter.formatFiat(userCurrency.code, token.fiat)
                )
                items.add(item)
            }
        } else {
            for ((index, currency) in currencies.withIndex()) {
                val item = Item.Token(
                    position = ListCell.getPosition(currencies.size, index),
                    currency = currency,
                    selected = currency == args.selectedCurrency,
                    fiatFormatted = null
                )
                items.add(item)
            }
        }
        items.toList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null).filterNotNull()

    fun setSearchQuery(query: String) {
        _searchQueryFlow.tryEmit(query)
    }

    private suspend fun getTokens() = tokenRepository.get(
        currency = settingsRepository.currency,
        accountId = wallet.accountId,
        network = wallet.network
    ) ?: emptyList()
}