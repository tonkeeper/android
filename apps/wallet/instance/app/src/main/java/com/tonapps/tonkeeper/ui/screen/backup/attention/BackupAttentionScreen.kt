package com.tonapps.tonkeeper.ui.screen.backup.attention

import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.lifecycle.lifecycleScope
import com.tonapps.tonkeeper.extensions.toast
import com.tonapps.tonkeeper.ui.screen.phrase.PhraseScreen
import com.tonapps.tonkeeperx.R
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import uikit.base.BaseFragment
import uikit.navigation.Navigation.Companion.navigation
import uikit.widget.HeaderView

class BackupAttentionScreen: BaseFragment(R.layout.fragment_backup_attention), BaseFragment.Modal {

    private val backupId: Long by lazy { arguments?.getLong(ARG_BACKUP_ID) ?: 0 }

    private val backupAttentionViewModel: BackupAttentionViewModel by viewModel { parametersOf(backupId) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<HeaderView>(R.id.header).doOnActionClick = { finish() }
        view.findViewById<Button>(R.id.continue_button).setOnClickListener { startBackup() }
        view.findViewById<Button>(R.id.cancel_button).setOnClickListener { finish() }
    }

    private fun startBackup() {
        backupAttentionViewModel.getRecoveryPhrase(requireContext()).catch {
            navigation?.toast(Localization.authorization_required)
        }.filterNotNull().onEach {
            navigation?.add(PhraseScreen.newInstance(it, true, backupId))
            finish()
        }.launchIn(lifecycleScope)
    }

    companion object {

        private const val ARG_BACKUP_ID = "backup_id"

        fun newInstance(backupId: Long): BackupAttentionScreen {
            val fragment = BackupAttentionScreen()
            fragment.arguments = Bundle().apply {
                putLong(ARG_BACKUP_ID, backupId)
            }
            return fragment
        }
    }
}