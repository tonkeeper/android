package com.tonapps.tonkeeper.ui.screen.init.step

import android.os.Bundle
import android.view.View
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.tonapps.onboading.screens.backup.BackupCheckData
import com.tonapps.onboading.screens.backup.BackupCheckScreen
import com.tonapps.tonkeeper.Environment
import com.tonapps.tonkeeper.ui.screen.init.InitViewModel
import com.tonapps.tonkeeperx.BuildConfig
import com.tonapps.tonkeeperx.R
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import ui.theme.MoonTheme
import uikit.base.BaseFragment

class BackupCheckInitScreen : BaseFragment(R.layout.fragment_compose_host) {

    override val fragmentName: String = "BackupCheckInitScreen"

    override val secure: Boolean = !BuildConfig.DEBUG

    private val initViewModel: InitViewModel by viewModel(ownerProducer = { requireParentFragment() })
    private val environment: Environment by inject()

    private val mnemonic: List<String>? by lazy { initViewModel.getMnemonic() }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // No phrase to quiz against (unexpected) — close this step instead of rendering an empty quiz.
        val words = mnemonic
        if (words.isNullOrEmpty()) {
            initViewModel.routePopBackStack()
            return
        }

        view.findViewById<ComposeView>(R.id.compose_view).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MoonTheme(colorScheme = environment.theme) {
                    val topOffset by initViewModel.uiTopOffset.collectAsState()
                    BackupCheckScreen(
                        data = BackupCheckData(words),
                        onDone = { initViewModel.completeBackup() },
                        onError = { initViewModel.trackBackupError() },
                        modifier = Modifier
                            .padding(top = with(LocalDensity.current) { topOffset.toDp() })
                            .navigationBarsPadding(),
                    )
                }
            }
        }
    }

    companion object {
        fun newInstance() = BackupCheckInitScreen()
    }
}
