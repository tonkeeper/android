package com.tonapps.tonkeeper.fragment.nft

import uikit.mvi.UiEffect

sealed class NftScreenEffect: UiEffect() {
    data object FailedLoad : NftScreenEffect()
}