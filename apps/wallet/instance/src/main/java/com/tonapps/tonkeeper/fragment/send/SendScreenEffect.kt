package com.tonapps.tonkeeper.fragment.send

import uikit.mvi.UiEffect

sealed class SendScreenEffect: UiEffect() {

    data object OpenCamera: SendScreenEffect()

    data object Finish: SendScreenEffect()

}