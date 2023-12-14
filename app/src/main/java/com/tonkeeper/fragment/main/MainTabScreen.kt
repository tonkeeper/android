package com.tonkeeper.fragment.main

import uikit.mvi.UiEffect
import uikit.mvi.UiFeature
import uikit.mvi.UiScreen
import uikit.mvi.UiState

abstract class MainTabScreen<S: UiState, E: UiEffect, F: UiFeature<S, E>>(
    layoutRes: Int
): UiScreen<S, E, F>(layoutRes) {
    abstract fun onUpScroll()
}