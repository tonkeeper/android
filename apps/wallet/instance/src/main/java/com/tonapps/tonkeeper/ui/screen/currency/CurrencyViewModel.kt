package com.tonapps.tonkeeper.ui.screen.currency

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonapps.tonkeeper.ui.screen.currency.list.Item
import com.tonapps.uikit.list.ListCell
import com.tonapps.wallet.data.core.WalletCurrency
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

class CurrencyViewModel(
    private val settings: SettingsRepository
): ViewModel() {

    private val _uiItemsFlow = MutableStateFlow<List<Item>>(emptyList())
    val uiItemsFlow = _uiItemsFlow.asStateFlow().filter { it.isNotEmpty() }

    init {
        settings.currencyFlow.map { buildUiItems(it) }.onEach {
            _uiItemsFlow.value = it
        }.launchIn(viewModelScope)
    }

    fun selectCurrency(currency: String) {
        settings.currency = WalletCurrency(currency)
    }

    private fun buildUiItems(selectedCurrency: WalletCurrency): List<Item> {
        val items = mutableListOf<Item>()
        for ((index, currency) in WalletCurrency.FIAT.withIndex()) {
            val item = Item(
                currency = currency,
                nameResId = getNameResIdForCurrency(currency),
                selected = currency == selectedCurrency.code,
                position = ListCell.getPosition(WalletCurrency.FIAT.size, index)
            )
            items.add(item)
        }
        return items
    }

    @StringRes
    private fun getNameResIdForCurrency(currency: String): Int {
        return when(currency.lowercase()) {
            "usd" -> Localization.currency_usd_name
            "eur" -> Localization.currency_eur_name
            "rub" -> Localization.currency_rub_name
            "aed" -> Localization.currency_aed_name
            "uah" -> Localization.currency_uah_name
            "uzs" -> Localization.currency_uzs_name
            "gbp" -> Localization.currency_gbp_name
            "chf" -> Localization.currency_chf_name
            "cny" -> Localization.currency_cny_name
            "krw" -> Localization.currency_krw_name
            "idr" -> Localization.currency_idr_name
            "inr" -> Localization.currency_inr_name
            "jpy" -> Localization.currency_jpy_name
            "ton" -> Localization.toncoin
            "kzt" -> Localization.currency_kzt_name
            else -> throw IllegalArgumentException("Unsupported currency: $currency")
        }

    }

}