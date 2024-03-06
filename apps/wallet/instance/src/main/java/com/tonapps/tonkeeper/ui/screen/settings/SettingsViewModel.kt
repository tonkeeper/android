package com.tonapps.tonkeeper.ui.screen.settings

import androidx.lifecycle.ViewModel
import com.tonapps.tonkeeper.ui.screen.settings.list.Item
import com.tonapps.uikit.list.ListCell
import com.tonapps.wallet.data.account.WalletRepository
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.core.WalletCurrency
import com.tonapps.wallet.data.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import uikit.extensions.collectFlow

class SettingsViewModel(
    private val walletRepository: WalletRepository,
    private val settings: SettingsRepository
): ViewModel() {

    private data class Data(
        val wallet: WalletEntity? = null,
        val currency: WalletCurrency? = null,
    )

    private val _dataFlow = MutableStateFlow(Data())
    private val dataFlow = _dataFlow.asStateFlow().filter { it.wallet != null && it.currency != null }

    private val _uiItemsFlow = MutableStateFlow<List<Item>>(emptyList())
    val uiItemsFlow = _uiItemsFlow.asStateFlow().filter { it.isNotEmpty() }

    init {
        collectFlow(walletRepository.activeWalletFlow) { _dataFlow.value = _dataFlow.value.copy(wallet = it) }
        collectFlow(settings.currencyFlow) { _dataFlow.value = _dataFlow.value.copy(currency = it) }

        collectFlow(dataFlow) {
            val wallet = it.wallet ?: return@collectFlow
            val currency = it.currency ?: return@collectFlow
            buildUiItems(wallet, currency)
        }
    }

    private fun buildUiItems(
        wallet: WalletEntity,
        currency: WalletCurrency
    ) {
        val uiItems = mutableListOf<Item>()
        uiItems.add(Item.Account(wallet))
        uiItems.add(Item.Space)
        uiItems.add(Item.Currency(currency.code, ListCell.Position.FIRST))
        uiItems.add(Item.Language(settings.language, ListCell.Position.LAST))
        _uiItemsFlow.value = uiItems
    }
}