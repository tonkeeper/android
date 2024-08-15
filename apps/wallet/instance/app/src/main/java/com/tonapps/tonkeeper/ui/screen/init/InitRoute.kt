package com.tonapps.tonkeeper.ui.screen.init

sealed class InitRoute {
    data object CreatePasscode: InitRoute()
    data object ReEnterPasscode: InitRoute()
    data object ImportWords: InitRoute()
    data object WatchAccount: InitRoute()
    data object LabelAccount: InitRoute()
    data object SelectAccount: InitRoute()
    // data object Push: InitRoute()
}