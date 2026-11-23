package com.tonapps.tonkeeper.ui.screen.events.compose

import android.content.Context
import com.tonapps.async.Async
import com.tonapps.blockchain.model.legacy.CommentEncryption
import com.tonapps.deposit.usecase.sign.SignTransaction
import com.tonapps.log.L
import com.tonapps.tonkeeper.extensions.toast
import com.tonapps.tonkeeper.ui.screen.dialog.encrypted.EncryptedCommentScreen
import com.tonapps.wallet.data.events.EventsRepository
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.passcode.PasscodeReason
import com.tonapps.wallet.data.passcode.WalletUnlocker
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.features.events.data.EncryptedCommentDecryptor
import com.tonapps.wallet.features.events.data.EncryptedCommentEntity
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import uikit.navigation.Navigation.Companion.navigation

class McEncryptedCommentDecryptor(
    private val settingsRepository: SettingsRepository,
    private val walletUnlocker: WalletUnlocker,
    private val signTransaction: SignTransaction,
    private val eventsRepository: EventsRepository,
    private val accountRepository: McAccountRepository,
) : EncryptedCommentDecryptor {

    override suspend fun decrypt(context: Context, walletId: String, comment: EncryptedCommentEntity): Boolean {
        return try {
            if (accountRepository.getWallet(walletId)?.isHardware == true) {
                withContext(Dispatchers.Main) {
                    context.navigation?.toast(Localization.ledger_mc_encrypted_comment_unsupported)
                }
                return false
            }
            if (settingsRepository.showEncryptedCommentModal) {
                val noShowAgain = withContext(Dispatchers.Main) {
                    EncryptedCommentScreen.show(context)
                } ?: return false
                settingsRepository.showEncryptedCommentModal = !noShowAgain
            }
            withContext(Async.Io) {
                val cryptoWallet = walletUnlocker.unlockWallet(walletId, PasscodeReason.DECRYPT_COMMENT)
                val privateKey = signTransaction.multichainTonPrivateKey(cryptoWallet)
                val decrypted = CommentEncryption.decryptComment(
                    privateKey.publicKey(),
                    privateKey,
                    comment.cipherText,
                    comment.senderAddress,
                )
                eventsRepository.saveDecryptedComment(comment.cipherText, decrypted)
                true
            }
        } catch (e: Throwable) {
            if (e is CancellationException) {
                currentCoroutineContext().ensureActive()
            }
            L.e(e)
            false
        }
    }
}
