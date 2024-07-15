package com.tonapps.tonkeeper.ui.screen.backup.main

import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import com.tonapps.tonkeeper.extensions.toast
import com.tonapps.tonkeeper.ui.screen.backup.attention.BackupAttentionScreen
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

class BackupScreen: BaseListFragment(), BaseFragment.SwipeBack {

    private val backupViewModel: BackupViewModel by viewModel()

    private val adapter = Adapter { item ->
        if (item is Item.RecoveryPhrase) {
            openRecoveryPhrase()
        } else if (item is Item.ManualBackup) {
            navigation?.add(BackupAttentionScreen.newInstance(0))
        } else if (item is Item.Backup) {
            navigation?.add(BackupAttentionScreen.newInstance(item.entity.id))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        collectFlow(backupViewModel.uiItemsFlow, adapter::submitList)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setAdapter(adapter)
        setTitle(getString(Localization.backup))
    }

    private fun openRecoveryPhrase() {
        backupViewModel.getRecoveryPhrase(requireContext()).catch {
            navigation?.toast(Localization.authorization_required)
        }.filterNotNull().onEach {
            navigation?.add(PhraseScreen.newInstance(it))
        }.launchIn(lifecycleScope)
    }

    companion object {
        fun newInstance() = BackupScreen()
    }

}