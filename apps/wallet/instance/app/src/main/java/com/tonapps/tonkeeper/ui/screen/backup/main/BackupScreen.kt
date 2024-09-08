package com.tonapps.tonkeeper.ui.screen.backup.main

import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import com.tonapps.tonkeeper.extensions.toast
import com.tonapps.tonkeeper.koin.walletViewModel
import com.tonapps.tonkeeper.ui.base.BaseListWalletScreen
import com.tonapps.tonkeeper.ui.base.ScreenContext
import com.tonapps.tonkeeper.ui.screen.backup.main.list.Adapter
import com.tonapps.tonkeeper.ui.screen.backup.main.list.Item
import com.tonapps.tonkeeper.ui.screen.phrase.PhraseScreen
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import uikit.base.BaseFragment
import uikit.base.BaseListFragment
import uikit.extensions.collectFlow
import uikit.navigation.Navigation.Companion.navigation

class BackupScreen(wallet: WalletEntity): BaseListWalletScreen<ScreenContext.Wallet>(ScreenContext.Wallet(wallet)), BaseFragment.SwipeBack {

    private val attentionDialog: BackupAttentionDialog by lazy {
        BackupAttentionDialog(requireContext())
    }

    override val viewModel: BackupViewModel by walletViewModel()

    private val adapter = Adapter { item ->
        when (item) {
            is Item.RecoveryPhrase -> attentionDialog.show {
                openRecoveryPhrase()
            }
            is Item.ManualBackup -> attentionDialog.show {
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
        viewModel.getRecoveryPhrase(requireContext()) { words ->
            navigation?.add(PhraseScreen.newInstance(screenContext.wallet, words, backup, backupId))
        }
    }

    companion object {
        fun newInstance(wallet: WalletEntity) = BackupScreen(wallet)
    }

}