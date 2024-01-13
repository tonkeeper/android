package com.tonkeeper.fragment.wallet.history

import uikit.mvi.UiEffect

sealed class HistoryScreenEffect: UiEffect() {
    data object UpScroll: HistoryScreenEffect()
}