package com.tonapps.tonkeeper.fragment.receive

import uikit.mvi.UiEffect

sealed class ReceiveScreenEffect: UiEffect() {

    data class Share(val address: String): ReceiveScreenEffect()

    data class Copy(val address: String): ReceiveScreenEffect()
}