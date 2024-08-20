package com.tonapps.tonkeeper.ui.screen.backup.main

import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import com.tonapps.tonkeeper.extensions.toast
import com.tonapps.tonkeeper.ui.base.BaseListWalletScreen
import com.tonapps.tonkeeper.ui.screen.backup.main.list.Adapter
import com.tonapps.tonkeeper.ui.screen.backup.main.list.Item
import com.tonapps.tonkeeper.ui.screen.phrase.PhraseScreen
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.androidx.viewmodel.ext.android.viewModel
import uikit.base.BaseFragment
import uikit.base.BaseListFragment
import uikit.extensions.collectFlow
import uikit.navigation.Navigation.Companion.navigation

class BackupScreen: BaseListWalletScreen(), BaseFragment.SwipeBack {

    private val attentionDialog: BackupAttentionDialog by lazy {
        BackupAttentionDialog(requireContext())
    }

    override val viewModel: BackupViewModel by viewModel()

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
        viewModel.getRecoveryPhrase(requireContext()).catch {
            navigation?.toast(Localization.authorization_required)
        }.filterNotNull().onEach {
            navigation?.add(PhraseScreen.newInstance(it, backup, backupId))
        }.launchIn(lifecycleScope)
    }

    companion object {
        fun newInstance() = BackupScreen()
    }

}