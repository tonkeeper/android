package com.tonkeeper.fragment.send.amount

import com.tonkeeper.App
import ton.SupportedCurrency
import uikit.mvi.UiState

data class AmountScreenState(
    val value: Float = 0f,
    val currency: SupportedCurrency = App.settings.currency,
    val available: String = "",
    val rate: String = "0 ${App.settings.currency.code}",
    val insufficientBalance: Boolean = false,
    val remaining: String = "",
    val canContinue: Boolean = false,
    val maxActive: Boolean = false
): UiState()