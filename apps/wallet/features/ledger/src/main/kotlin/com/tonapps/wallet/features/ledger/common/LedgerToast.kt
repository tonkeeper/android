package com.tonapps.wallet.features.ledger.common

import android.content.Context
import com.tonapps.uikit.color.backgroundContentTintColor
import uikit.navigation.Navigation.Companion.navigation

internal fun Context.ledgerToast(message: String) {
    navigation?.toast(message = message, loading = false, color = backgroundContentTintColor)
}
