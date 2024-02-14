package com.tonapps.tonkeeper.fragment.currency

import androidx.annotation.StringRes
import com.tonapps.tonkeeper.App
import com.tonapps.wallet.localization.Localization
import com.tonapps.tonkeeper.core.widget.Widget
import com.tonapps.tonkeeper.event.ChangeCurrencyEvent
import com.tonapps.tonkeeper.fragment.currency.list.CurrencyItem
import com.tonapps.wallet.data.core.Currency
import core.EventBus
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

    fun setSelect(currency: String) {
        setSelect(Currency(currency))
    }

    fun setSelect(currency: Currency) {
        App.settings.currency = currency

        EventBus.post(ChangeCurrencyEvent(currency))

        Widget.updateAll()

        updateUiState {
            it.copy(
                items = buildItems()
            )
        }
    }

    private fun buildItems(): List<CurrencyItem> {
        val items = mutableListOf<CurrencyItem>()
        val codes = Currency.FIAT.toTypedArray()
        for ((index, currency) in codes.withIndex()) {
            items.add(
                CurrencyItem(
                    currency = currency,
                    nameResId = getNameResIdForCurrency(currency),
                    selected = currency == App.settings.currency.code,
                    position = com.tonapps.uikit.list.ListCell.getPosition(codes.size, index)
                )
            )
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