package com.tonapps.tonkeeper.ui.screen.events.compose

import com.tonapps.log.L
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.ui.screen.dialog.encrypted.EncryptedCommentScreen
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.blockchain.model.legacy.CommentEncryption
import com.tonapps.wallet.data.events.EventsRepository
import com.tonapps.wallet.data.events.tx.model.TxEvent
import com.tonapps.wallet.data.passcode.PasscodeManager
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object TxScope {

    suspend fun BaseWalletVM.decryptComment(
        wallet: WalletEntity,
        tx: TxEvent,
        actionIndex: Int,
        accountRepository: AccountRepository,
        settingsRepository: SettingsRepository,
        passcodeManager: PasscodeManager,
        eventsRepository: EventsRepository,
    ): Boolean {
        try {
            val action = tx.actions[actionIndex]
            val encryptedText = action.encryptedText ?: return true
            val sender = action.sender ?: throw Exception("No sender account")
            if (settingsRepository.showEncryptedCommentModal) {
                val noShowAgain = withContext(Dispatchers.Main) {
                    EncryptedCommentScreen.show(context)
                } ?: throw Exception("User canceled")
                settingsRepository.showEncryptedCommentModal = !noShowAgain
            }
            if (!passcodeManager.confirmation(context, context.getString(Localization.app_name))) {
                throw Exception("Wrong passcode")
            }
            val privateKey = accountRepository.getPrivateKey(wallet.id) ?: throw Exception("Private key not found")

            val decrypted = CommentEncryption.decryptComment(
                wallet.publicKey,
                privateKey,
                encryptedText.cipher,
                sender.address
            )
            eventsRepository.saveDecryptedComment(tx.hash, decrypted)
            return false
        } catch (e: Throwable) {
            L.e(e)
            return false
        }
    }
}
