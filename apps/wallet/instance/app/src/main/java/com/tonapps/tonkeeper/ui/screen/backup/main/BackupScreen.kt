package com.tonapps.tonkeeper.ui.screen.backup.main

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.tonapps.bus.core.AnalyticsHelper
import com.tonapps.bus.generated.Events.WalletFlow.WalletFlowSource
import com.tonapps.bus.generated.Events.WalletFlow.WalletFlowWalletMode
import com.tonapps.extensions.bestMessage
import com.tonapps.extensions.getEnum
import com.tonapps.extensions.putEnum
import com.tonapps.onboading.screens.backup.BackupAttentionDialog
import com.tonapps.tonkeeper.Environment
import com.tonapps.tonkeeper.Wallet
import com.tonapps.tonkeeper.extensions.toast
import com.tonapps.tonkeeper.ui.base.BaseListWalletScreen
import com.tonapps.tonkeeper.ui.base.ScreenContext
import com.tonapps.tonkeeper.ui.screen.backup.main.list.Adapter
import com.tonapps.tonkeeper.ui.screen.backup.main.list.Item
import com.tonapps.tonkeeper.ui.screen.phrase.PhraseScreen
import com.tonapps.wallet.localization.Localization
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import ui.theme.MoonTheme
import uikit.base.BaseFragment
import uikit.extensions.collectFlow

class BackupScreen : BaseListWalletScreen<ScreenContext.None>(ScreenContext.None), BaseFragment.SwipeBack {

    override val fragmentName: String = "BackupScreen"

    private val source: WalletFlowSource by lazy {
        requireArguments().getEnum(ARG_SOURCE, WalletFlowSource.Settings)
    }

    private val environment: Environment by inject()

    private var attentionHost: ComposeView? = null

    override val viewModel: BackupViewModel by viewModel()

    private val adapter = Adapter { item ->
        when (item) {
            is Item.RecoveryPhrase -> showAttention {
                openRecoveryPhrase()
            }
            is Item.ManualBackup, Item.ManualAccentBackup -> showAttention {
                openRecoveryPhrase(backup = true)
            }
            is Item.Backup -> showAttention {
                openRecoveryPhrase(backup = true, backupId = item.entity.id)
            }
            else -> { }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setAdapter(adapter)
        setTitle(getString(Localization.backup))
        collectFlow(viewModel.uiItemsFlow, adapter::submitList)
    }

    override fun onDestroyView() {
        removeAttentionHost()
        super.onDestroyView()
    }

    private fun showAttention(onConfirm: () -> Unit) {
        removeAttentionHost()
        val root = requireActivity().findViewById<ViewGroup>(android.R.id.content)
        val host = ComposeView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                MoonTheme(colorScheme = environment.theme) {
                    BackupAttentionDialog(
                        onConfirm = onConfirm,
                        onClose = { removeAttentionHost() },
                    )
                }
            }
        }
        attentionHost = host
        root.addView(host)
    }

    private fun removeAttentionHost() {
        attentionHost?.let { host ->
            (host.parent as? ViewGroup)?.removeView(host)
        }
        attentionHost = null
    }

    private fun openRecoveryPhrase(backup: Boolean = false, backupId: Long = 0) {
        val ctx = context ?: return

        viewModel.getRecoveryPhrase(ctx) { words, error ->
            if (error != null) {
                if (backup) {
                    trackBackupError(error)
                }
                navigation?.toast(error.bestMessage)
            } else {
                if (backup) {
                    trackBackupStarted()
                }
                navigation?.add(PhraseScreen.newInstance(words, backup, backupId, source = source))
            }
        }
    }

    private fun trackBackupStarted() {
        AnalyticsHelper.Default.events.walletFlow.walletBackupStarted(
            walletMode = walletMode() ?: return,
            source = source,
        )
    }

    private fun trackBackupError(error: Throwable) {
        AnalyticsHelper.Default.events.walletFlow.walletBackupError(
            walletMode = walletMode() ?: return,
            source = source,
            errorType = error.javaClass.simpleName,
            errorCode = null,
            errorMessage = error.message,
        )
    }

    private fun walletMode(): WalletFlowWalletMode? = when (viewModel.walletFlow.value) {
        is Wallet.Legacy -> WalletFlowWalletMode.Single
        is Wallet.Multichain -> WalletFlowWalletMode.Multi
        null -> null
    }

    companion object {

        private const val ARG_SOURCE = "source"

        fun newInstance(source: WalletFlowSource): BackupScreen {
            val fragment = BackupScreen()
            fragment.arguments = Bundle().apply {
                putEnum(ARG_SOURCE, source)
            }
            return fragment
        }
    }
}
