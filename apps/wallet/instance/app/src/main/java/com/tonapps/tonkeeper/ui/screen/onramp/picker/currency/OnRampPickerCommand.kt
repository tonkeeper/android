package com.tonapps.tonkeeper.ui.screen.onramp.picker.currency

import com.tonapps.wallet.data.core.currency.WalletCurrency

sealed class OnRampPickerCommand {
    data class OpenCurrencyPicker(
        val localization: Int,
        val currencies: List<WalletCurrency>,
        val extras: List<String>,
    ): OnRampPickerCommand()
    data object Main: OnRampPickerCommand()
    data object Finish: OnRampPickerCommand()
    data class SetCurrency(val currency: WalletCurrency): OnRampPickerCommand()
}