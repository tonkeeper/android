package com.tonapps.tonkeeper.ui.screen.onramp.picker.provider

import android.app.Application
import com.tonapps.icu.Coins
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.ui.screen.onramp.main.state.UiState
import com.tonapps.tonkeeper.ui.screen.onramp.picker.provider.list.Item
import com.tonapps.uikit.list.ListCell
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.wallet.data.purchase.PurchaseRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class OnRampProviderPickerViewModel(
    app: Application,
    private val state: UiState.SelectedProvider,
): BaseWalletVM(app) {

    private val _stateFlow = MutableStateFlow(state)
    val stateFlow = _stateFlow.asStateFlow()

    val uiItemsFlow = stateFlow.map { state ->
        val fromFormat = CurrencyFormatter.format(state.send.code, Coins.ONE, replaceSymbol = false)
        val selectedProvider = state.selectedProvider!!
        val list = mutableListOf<Item>()
        for ((index, provider) in state.providers.withIndex()) {
            val minAmount = Coins.of(provider.minAmount, state.send.decimals)
            val minAmountFormat = if (provider.minAmount > 0) CurrencyFormatter.format(state.send.code, minAmount, replaceSymbol = false) else ""
            val rate =  if (provider.minAmount > 0) Coins.of(provider.receive / provider.minAmount, state.receive.decimals) else state.calculateRate(provider.receive)
            val rateFormat = CurrencyFormatter.format(state.receive.code, rate, replaceSymbol = false)
            val position = ListCell.getPosition(state.providers.size, index)
            val item = Item(
                position = position,
                provider = provider,
                selected = provider.id.equals(selectedProvider.id, true),
                rateFormat = "$fromFormat ≈ $rateFormat",
                best = index == 0 && state.providers.size > 1,
                minAmountFormat = minAmountFormat
            )
            list.add(item)
        }
        list.toList()
    }

    fun setSelectedProvider(providerId: String) {
        _stateFlow.update {
            it.copy(
                selectedProviderId = providerId
            )
        }
    }
}