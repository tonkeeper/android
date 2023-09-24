package com.tonkeeper.fragment.currency

import androidx.annotation.StringRes
import com.tonkeeper.R
import com.tonkeeper.fragment.currency.list.CurrencyItem
import com.tonkeeper.ton.SupportedCurrency
import com.tonkeeper.uikit.list.BaseListItem
import com.tonkeeper.uikit.mvi.UiFeature

class CurrencyScreenFeature: UiFeature<CurrencyScreenState>(CurrencyScreenState()) {

    init {
        val items = mutableListOf<CurrencyItem>()
        val codes = SupportedCurrency.values()
        for ((index, currency) in codes.withIndex()) {
            items.add(
                CurrencyItem(
                    code = currency.code,
                    nameResId = getNameResIdForCurrency(currency),
                    selected = index == 0,
                    position = BaseListItem.Cell.getPosition(codes.size, index)
                )
            )
        }
        updateUiState {
            it.copy(
                items = items
            )
        }
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
            else -> throw IllegalArgumentException("Unsupported currency: $currency")
        }

    }
}