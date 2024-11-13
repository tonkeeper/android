package com.tonapps.tonkeeper.ui.screen.stories.w5

import android.app.Application
import android.content.Context
import com.tonapps.blockchain.ton.contract.WalletVersion
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.account.Wallet
import com.tonapps.wallet.data.backup.BackupRepository
import com.tonapps.wallet.data.backup.entities.BackupEntity
import com.tonapps.wallet.data.passcode.PasscodeManager
import com.tonapps.wallet.data.rn.RNLegacy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take

class W5StoriesViewModel(
    app: Application,
    private val accountRepository: AccountRepository,
    private val passcodeManager: PasscodeManager,
    private val backupRepository: BackupRepository,
    private val rnLegacy: RNLegacy,
): BaseWalletVM(app) {

    fun addWallet(context: Context) = accountRepository.selectedWalletFlow.take(1)
        .map { wallet ->
            val fixedLabel = wallet.label.name.replace(wallet.version.title, "") + " " + WalletVersion.V5R1.title
            accountRepository.addWallet(
                ids = listOf(AccountRepository.newWalletId()),
                label = Wallet.NewLabel(listOf(fixedLabel), wallet.label.emoji, wallet.label.color),
                publicKey = wallet.publicKey,
                versions = listOf(WalletVersion.V5R1),
                type = wallet.type
            ).first()
        }.map { wallet ->
            val mnemonic = accountRepository.getMnemonic(wallet.id) ?: throw Exception("mnemonic not found")
            val passcode = passcodeManager.requestValidPasscode(context)
            rnLegacy.addMnemonics(passcode, listOf(wallet.id), mnemonic.toList())
            wallet
        }.map { wallet ->
            backupRepository.addBackup(wallet.id, BackupEntity.Source.LOCAL)
            wallet.id
        }.flowOn(Dispatchers.IO)

}