package com.tonapps.tonkeeper.extensions

import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.tonapps.security.tryCallGC
import com.tonapps.tonkeeper.ui.screen.send.main.SendException
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.rn.RNException
import com.tonapps.wallet.data.rn.RNLegacy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.ton.api.pk.PrivateKeyEd25519
import org.ton.mnemonic.Mnemonic
import uikit.navigation.NavigationActivity


suspend fun AccountRepository.sign(
    activity: NavigationActivity,
    rnLegacy: RNLegacy,
    walletId: String,
    bytes: ByteArray
): ByteArray {
    var privateKey: PrivateKeyEd25519? = requestPrivateKey(activity, rnLegacy, walletId)
    val signature = privateKey?.sign(bytes) ?: throw SendException.UnableSendTransaction()
    privateKey = null
    tryCallGC()
    return signature
}

suspend fun AccountRepository.requestPrivateKey(
    activity: NavigationActivity,
    rnLegacy: RNLegacy,
    walletId: String,
): PrivateKeyEd25519? = withContext(Dispatchers.IO) {
    try {
        val privateKeyEd25519 = getPrivateKey(walletId)
        if (privateKeyEd25519 != null) {
            privateKeyEd25519
        } else {
            val vaultState = rnLegacy.requestVault(activity)
            val mnemonic = vaultState.getDecryptedData(walletId)?.mnemonic ?: throw RNException.NotFoundMnemonic(walletId)
            val seed = Mnemonic.toSeed(splitMnemonic(mnemonic))
            PrivateKeyEd25519(seed)
        }
    } catch (e: Throwable) {
        FirebaseCrashlytics.getInstance().recordException(e)
        null
    }
}

private fun splitMnemonic(mnemonic: String): List<String> {
    val words = if (mnemonic.contains(",")) {
        mnemonic.split(",")
    } else {
        mnemonic.split(" ")
    }
    return words.map { it.trim() }
}

