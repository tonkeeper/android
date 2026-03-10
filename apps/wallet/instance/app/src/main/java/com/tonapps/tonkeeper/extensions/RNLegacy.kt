package com.tonapps.tonkeeper.extensions

import com.tonapps.wallet.data.passcode.dialog.PasscodeDialog
import com.tonapps.wallet.data.rn.RNException
import com.tonapps.wallet.data.rn.RNLegacy
import com.tonapps.wallet.data.rn.data.RNVaultState
import com.tonapps.wallet.data.rn.data.RNWallets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uikit.navigation.NavigationActivity

suspend fun RNLegacy.requestVault(
    activity: NavigationActivity
): RNVaultState = withContext(Dispatchers.IO) {
    val wallets = getWallets()
    if (wallets.count == 0) {
        throw IllegalStateException("No wallets found")
    }
    val passcode = requestPasscode(activity, wallets)
    getVaultState(passcode)
}

private suspend fun RNLegacy.requestPasscode(
    activity: NavigationActivity,
    wallets: RNWallets
): String = withContext(Dispatchers.Main) {
    val passcodeFromBiometry = if (wallets.biometryEnabled) {
        exportPasscodeWithBiometry()
    } else {
        null
    }
    val passcode = if (!passcodeFromBiometry.isNullOrBlank()) {
        passcodeFromBiometry
    } else {
        PasscodeDialog.request(activity)
    }

    passcode ?: throw RNException.NotFoundPasscode
}