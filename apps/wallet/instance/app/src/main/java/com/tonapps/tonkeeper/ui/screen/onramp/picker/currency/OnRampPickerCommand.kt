package com.tonapps.tonkeeper.ui.screen.onramp.picker.currency

import com.tonapps.wallet.data.core.currency.WalletCurrency

sealed class OnRampPickerCommand {
    data class OpenCurrencyPicker(val currencies: List<WalletCurrency>): OnRampPickerCommand()
    data object Main: OnRampPickerCommand()
    data object Finish: OnRampPickerCommand()
}