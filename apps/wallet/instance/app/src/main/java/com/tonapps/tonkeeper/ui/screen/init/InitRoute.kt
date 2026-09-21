package com.tonapps.tonkeeper.ui.screen.init

sealed class InitRoute {
    data object SelectType: InitRoute()
    data object CreatePasscode: InitRoute()
    data object ReEnterPasscode: InitRoute()
    data object EnterPasscode: InitRoute()
    data object ImportWords: InitRoute()
    data object WatchAccount: InitRoute()
    data object LabelAccount: InitRoute()
    data object BackupStart: InitRoute()
    data object BackupPhrase: InitRoute()
    data object BackupCheck: InitRoute()
    data object SelectAccount: InitRoute()
    data object SelectWalletVersion: InitRoute()
    data object SelectMnemonicType: InitRoute()
    data object Push: InitRoute()
}
