package com.tonkeeper.fragment.currency

import androidx.annotation.StringRes
import com.tonkeeper.App
import com.tonkeeper.R
import com.tonkeeper.event.ChangeCurrencyEvent
import com.tonkeeper.fragment.currency.list.CurrencyItem
import ton.SupportedCurrency
import core.EventBus
import uikit.list.ListCell
import uikit.mvi.UiFeature

class CurrencyScreenFeature: UiFeature<CurrencyScreenState, CurrencyScreenEffect>(CurrencyScreenState()) {

    init {
        requestItems()
    }

    private fun requestItems() {
        updateUiState {
            it.copy(
                items = buildItems()
            )
        }
    }

    fun setSelect(currency: SupportedCurrency) {
        App.settings.currency = currency

        EventBus.post(ChangeCurrencyEvent(currency))

        updateUiState {
            it.copy(
                items = buildItems()
            )
        }
    }

    private fun buildItems(): List<CurrencyItem> {
        val items = mutableListOf<CurrencyItem>()
        val codes = SupportedCurrency.values()
        for ((index, currency) in codes.withIndex()) {
            items.add(
                CurrencyItem(
                    currency = currency,
                    nameResId = getNameResIdForCurrency(currency),
                    selected = currency == App.settings.currency,
                    position = ListCell.getPosition(codes.size, index)
                )
            )
        }
        return items
    }

    @StringRes
    private fun getNameResIdForCurrency(currency: SupportedCurrency): Int {
        return when(currency) {
            SupportedCurrency.USD -> R.string.currency_usd_name
            SupportedCurrency.EUR -> R.string.currency_eur_name
            SupportedCurrency.RUB -> R.string.currency_rub_name
            SupportedCurrency.AED -> R.string.currency_aed_name
            SupportedCurrency.UAH -> R.string.currency_uah_name
            SupportedCurrency.UZS -> R.string.currency_uzs_name
            SupportedCurrency.GBP -> R.string.currency_gbp_name
            SupportedCurrency.CHF -> R.string.currency_chf_name
            SupportedCurrency.CNY -> R.string.currency_cny_name
            SupportedCurrency.KRW -> R.string.currency_krw_name
            SupportedCurrency.IDR -> R.string.currency_idr_name
            SupportedCurrency.INR -> R.string.currency_inr_name
            SupportedCurrency.JPY -> R.string.currency_jpy_name
            SupportedCurrency.TON -> R.string.toncoin
            else -> throw IllegalArgumentException("Unsupported currency: $currency")
        }

    }
}