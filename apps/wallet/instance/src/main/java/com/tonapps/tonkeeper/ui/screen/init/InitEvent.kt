package com.tonapps.tonkeeper.ui.screen.init

sealed class InitEvent {
    data class Loading(val loading: Boolean): InitEvent()
    data object Back: InitEvent()
    data object Finish: InitEvent()

    open class Step: InitEvent() {
        object CreatePasscode: Step()
        object ReEnterPasscode: Step()
        object ImportWords: Step()
        object WatchAccount: Step()
        object LabelAccount: Step()
        object SelectAccount: Step()
    }
}