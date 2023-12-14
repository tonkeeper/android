package com.tonkeeper.fragment.wallet.main

import uikit.mvi.UiEffect

sealed class WalletScreenEffect: UiEffect() {

    data class CopyAddress(val address: String): WalletScreenEffect()
}