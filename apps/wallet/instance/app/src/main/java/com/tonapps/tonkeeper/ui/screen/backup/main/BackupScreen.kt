package com.tonapps.tonkeeper.ui.screen.backup.main

import android.os.Bundle
import android.view.View
import com.tonapps.extensions.bestMessage
import com.tonapps.tonkeeper.extensions.toast
import com.tonapps.tonkeeper.koin.walletViewModel
import com.tonapps.tonkeeper.ui.base.BaseListWalletScreen
import com.tonapps.tonkeeper.ui.base.ScreenContext
import com.tonapps.tonkeeper.ui.screen.backup.main.list.Adapter
import com.tonapps.tonkeeper.ui.screen.backup.main.list.Item
import com.tonapps.tonkeeper.ui.screen.phrase.PhraseScreen
import com.tonapps.tonkeeperx.BuildConfig
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.localization.Localization
import uikit.base.BaseFragment
import uikit.extensions.collectFlow

class BackupScreen(wallet: WalletEntity): BaseListWalletScreen<ScreenContext.Wallet>(ScreenContext.Wallet(wallet)), BaseFragment.SwipeBack {

    override val fragmentName: String = "BackupScreen"

    private val attentionDialog: BackupAttentionDialog by lazy {
        BackupAttentionDialog(requireContext())
    }

    override val viewModel: BackupViewModel by walletViewModel()

    private val adapter = Adapter { item ->
        when (item) {
            is Item.RecoveryPhrase -> attentionDialog.show {
                openRecoveryPhrase()
            }
            is Item.ManualBackup, Item.ManualAccentBackup -> attentionDialog.show {
                openRecoveryPhrase(backup = true)
            }
            is Item.Backup -> attentionDialog.show {
                openRecoveryPhrase(backup = true, backupId = item.entity.id)
            }
            else -> { }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        collectFlow(viewModel.uiItemsFlow, adapter::submitList)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setAdapter(adapter)
        setTitle(getString(Localization.backup))
    }

    private fun openRecoveryPhrase(backup: Boolean = false, backupId: Long = 0) {
        viewModel.getRecoveryPhrase(requireContext()) { words, error ->
            if (error != null) {
                navigation?.toast(error.bestMessage)
            } else {
                navigation?.add(PhraseScreen.newInstance(screenContext.wallet, words, backup, backupId))
            }
        }
    }

    companion object {
        fun newInstance(wallet: WalletEntity) = BackupScreen(wallet)
    }

}