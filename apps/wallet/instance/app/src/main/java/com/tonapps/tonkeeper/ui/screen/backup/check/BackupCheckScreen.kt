package com.tonapps.tonkeeper.ui.screen.backup.check

import android.os.Bundle
import android.view.View
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.tonapps.bus.generated.Events.WalletFlow.WalletFlowSource
import com.tonapps.onboading.screens.backup.BackupCheckData
import com.tonapps.onboading.screens.backup.BackupCheckScreen as BackupCheckContent
import com.tonapps.tonkeeper.Environment
import com.tonapps.tonkeeper.ui.base.BaseWalletScreen
import com.tonapps.tonkeeper.ui.base.ScreenContext
import com.tonapps.tonkeeperx.BuildConfig
import com.tonapps.tonkeeperx.R
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import ui.theme.MoonTheme
import uikit.base.BaseFragment

class BackupCheckScreen : BaseWalletScreen<ScreenContext.None>(R.layout.fragment_compose_host, ScreenContext.None), BaseFragment.SwipeBack {

    override val fragmentName: String = "BackupCheckScreen"

    private val args: BackupCheckArgs by lazy { BackupCheckArgs(requireArguments()) }

    override val secure: Boolean = !BuildConfig.DEBUG

    override val viewModel: BackupCheckViewModel by viewModel()

    private val environment: Environment by inject()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<ComposeView>(R.id.compose_view).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MoonTheme(colorScheme = environment.theme) {
                    BackupCheckContent(
                        data = BackupCheckData(args.words.toList()),
                        onDone = { viewModel.saveBackup(args.backupId, args.source) { finish() } },
                        modifier = Modifier.systemBarsPadding(),
                        onBack = { finish() },
                        onError = { viewModel.trackBackupError(args.source) },
                    )
                }
            }
        }
    }

    companion object {

        fun newInstance(
            words: Array<String>,
            backupId: Long,
            source: WalletFlowSource
        ): BackupCheckScreen {
            val fragment = BackupCheckScreen()
            fragment.setArgs(BackupCheckArgs(words, backupId, source))
            return fragment
        }

    }

}
