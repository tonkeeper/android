package com.tonapps.tonkeeper.fragment.currency

import com.tonapps.tonkeeper.fragment.currency.list.CurrencyItem
import uikit.mvi.UiState

data class CurrencyScreenState(
    val items: List<CurrencyItem> = emptyList()
): UiState()