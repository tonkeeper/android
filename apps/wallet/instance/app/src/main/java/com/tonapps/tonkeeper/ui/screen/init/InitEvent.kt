package com.tonapps.tonkeeper.ui.screen.init

sealed class InitEvent {
    data class Loading(val loading: Boolean): InitEvent()
    data object Back: InitEvent()
    data object Finish: InitEvent()
}