package com.tonapps.wallet.data.settings.entities

import com.tonapps.wallet.data.core.WalletCurrency

data class SettingsEntity(
    val currency: WalletCurrency,
    val country: String,
)