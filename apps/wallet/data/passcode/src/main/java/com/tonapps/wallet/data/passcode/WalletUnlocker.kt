package com.tonapps.wallet.data.passcode

import com.tonapps.chainkit.core.chain.model.account.MnemonicWallet
import com.tonapps.security.multichain.MnemonicCoder
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Opens the multichain vault behind the passcode prompt. The prompt shows on the screen the user is
 * looking at, so callers hold no Context; the screen is only referenced while the prompt is up.
 */
interface WalletUnlocker {

    /**
     * Asks for the passcode on the foreground screen, naming [reason]; throws CancellationException
     * when it is closed.
     */
    suspend fun unlockWallet(walletId: String, reason: PasscodeReason): MnemonicWallet

    /** Asks for the passcode as [unlockWallet] does and runs [block] with the vault open. */
    suspend fun <T> unlockVault(reason: PasscodeReason, block: suspend (MnemonicCoder) -> T): T

    /**
     * Asks for the passcode as [unlockWallet] does without opening the vault, for keys kept outside it;
     * false when the prompt is closed or no screen is in front.
     */
    suspend fun confirm(reason: PasscodeReason): Boolean
}

internal class ForegroundWalletUnlocker(
    private val passcodeManager: PasscodeManager,
    private val accountRepository: McAccountRepository,
    private val foreground: ForegroundActivityTracker,
) : WalletUnlocker {

    override suspend fun unlockWallet(walletId: String, reason: PasscodeReason): MnemonicWallet {
        return unlockVault(reason) { coder ->
            accountRepository.getCryptoWallet(walletId, coder)
                ?: throw IllegalStateException("CryptoWallet not found for $walletId")
        }
    }

    override suspend fun <T> unlockVault(reason: PasscodeReason, block: suspend (MnemonicCoder) -> T): T {
        // No screen to ask on (the app went to the background, or a rotation outlasted the wait)
        // reads as a closed prompt: nothing was unlocked and the caller may simply ask again.
        val activity = foreground.awaitResumed(FOREGROUND_WAIT_MS)
            ?: throw CancellationException("no foreground screen for the passcode prompt")

        // The prompt switches to the main thread itself; the vault is opened off it.
        return withContext(Dispatchers.IO) {
            passcodeManager.unlockMultichainVault(activity, reason, block)
        }
    }

    override suspend fun confirm(reason: PasscodeReason): Boolean {
        val activity = foreground.awaitResumed(FOREGROUND_WAIT_MS) ?: return false
        return passcodeManager.confirmation(activity, reason)
    }

    internal companion object {
        // Covers a configuration change: the old screen pauses before the new one resumes.
        const val FOREGROUND_WAIT_MS = 2_000L
    }
}
