package com.tonkeeper.fragment.send.pager

import com.tonkeeper.fragment.send.SendScreen
import com.tonkeeper.fragment.send.SendScreenFeature
import uikit.mvi.UiEffect
import uikit.mvi.UiFeature
import uikit.mvi.UiScreen
import uikit.mvi.UiState

abstract class PagerScreen<S: UiState, E: UiEffect, F: UiFeature<S, E>>(
    layoutRes: Int
): UiScreen<S, E, F>(layoutRes) {

    val parentScreen: SendScreen?
        get() = parentFragment as? SendScreen

    val parentFeature: SendScreenFeature?
        get() = parentScreen?.feature

    var visible: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                onVisibleChange(value)
            }
        }


    open fun onVisibleChange(visible: Boolean) {

    }
}