package com.tonapps.tonkeeper.ui.screen.init.step

import android.content.res.Configuration
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.tonapps.blockchain.MnemonicHelper
import com.tonapps.onboading.screens.backup.BackupPhraseScreen as BackupPhraseContent
import com.tonapps.tonkeeper.Environment
import com.tonapps.tonkeeper.ui.screen.init.InitViewModel
import com.tonapps.tonkeeperx.R
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import ui.theme.MoonTheme
import uikit.base.BaseFragment

class BackupPhraseScreen : BaseFragment(R.layout.fragment_compose_host) {

    override val fragmentName: String = "BackupPhraseScreen"

    private val initViewModel: InitViewModel by viewModel(ownerProducer = { requireParentFragment() })
    private val environment: Environment by inject()

    private val words: List<String> by lazy {
        initViewModel.getMnemonicIndexes()?.map { MnemonicHelper.wordAt(it) } ?: emptyList()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val config =
            Configuration(requireContext().resources.configuration).apply { fontScale = 1f }
        val themedContext = ContextThemeWrapper(requireContext(), requireContext().theme)
        themedContext.applyOverrideConfiguration(config)
        val noScaleInflater = inflater.cloneInContext(themedContext)
        return super.onCreateView(noScaleInflater, container, savedInstanceState)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<ComposeView>(R.id.compose_view).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MoonTheme(colorScheme = environment.theme) {
                    val topOffset by initViewModel.uiTopOffset.collectAsState()
                    BackupPhraseContent(
                        words = words,
                        onCheckBackup = { initViewModel.navigateToBackupCheck() },
                        modifier = Modifier
                            .padding(top = with(LocalDensity.current) { topOffset.toDp() })
                            .navigationBarsPadding(),
                    )
                }
            }
        }
    }

    companion object {
        fun newInstance() = BackupPhraseScreen()
    }
}
