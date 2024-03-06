package com.tonapps.tonkeeper.fragment.settings.main

import android.view.View
import androidx.collection.ArrayMap
import com.tonapps.blockchain.ton.contract.WalletVersion
import uikit.mvi.UiEffect

sealed class SettingsScreenEffect: UiEffect() {
    data object Logout: SettingsScreenEffect()
    data object ReloadWallet: SettingsScreenEffect()
}