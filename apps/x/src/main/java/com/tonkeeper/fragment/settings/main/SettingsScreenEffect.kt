package com.tonkeeper.fragment.settings.main

import android.view.View
import androidx.collection.ArrayMap
import ton.contract.WalletVersion
import uikit.mvi.UiEffect

sealed class SettingsScreenEffect: UiEffect() {
    data object Logout: SettingsScreenEffect()

    data class SelectWalletVersion(
        val view: View,
        val current: WalletVersion,
        val wallets: ArrayMap<WalletVersion, String>,
    ): SettingsScreenEffect()

    data object ReloadWallet: SettingsScreenEffect()
}