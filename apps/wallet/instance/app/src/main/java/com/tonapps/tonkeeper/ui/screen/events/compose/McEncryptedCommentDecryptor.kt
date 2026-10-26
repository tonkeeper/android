package com.tonapps.tonkeeper.ui.screen.events.compose

import android.content.Context
import com.tonapps.async.Async
import com.tonapps.blockchain.model.legacy.CommentEncryption
import com.tonapps.deposit.usecase.sign.SignTransaction
import com.tonapps.log.L
import com.tonapps.tonkeeper.ui.screen.dialog.encrypted.EncryptedCommentScreen
import com.tonapps.wallet.data.events.EventsRepository
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.passcode.PasscodeManager
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.features.events.data.EncryptedCommentDecryptor
import com.tonapps.wallet.features.events.data.EncryptedCommentEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class McEncryptedCommentDecryptor(
    private val settingsRepository: SettingsRepository,
    private val passcodeManager: PasscodeManager,
    private val accountRepository: McAccountRepository,
    private val signTransaction: SignTransaction,
    private val eventsRepository: EventsRepository,
) : EncryptedCommentDecryptor {

    override suspend fun decrypt(context: Context, walletId: String, comment: EncryptedCommentEntity): Boolean {
        return try {
            if (settingsRepository.showEncryptedCommentModal) {
                val noShowAgain = withContext(Dispatchers.Main) {
                    EncryptedCommentScreen.show(context)
                } ?: return false
                settingsRepository.showEncryptedCommentModal = !noShowAgain
            }
            withContext(Async.Io) {
                val cryptoWallet = passcodeManager.unlockMultichainVault(context) { coder ->
                    accountRepository.getCryptoWallet(walletId, coder)
                } ?: return@withContext false
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
