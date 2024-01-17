package com.tonkeeper.extensions

import com.tonkeeper.App
import ton.wallet.Wallet


fun Wallet.isRecoveryPhraseBackup(): Boolean {
    return App.settings.isRecoveryPhraseBackup(accountId)
}

fun Wallet.setRecoveryPhraseBackup(isBackup: Boolean) {
    App.settings.setRecoveryPhraseBackup(accountId, isBackup)
}
