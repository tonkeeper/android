package com.tonkeeper.fragment.currency

import com.tonkeeper.fragment.currency.list.CurrencyItem
import com.tonkeeper.uikit.mvi.UiState

data class CurrencyScreenState(
    val items: List<CurrencyItem> = emptyList()
): UiState()