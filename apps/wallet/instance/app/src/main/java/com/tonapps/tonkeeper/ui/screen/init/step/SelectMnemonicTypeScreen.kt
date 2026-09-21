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
import com.tonapps.onboading.screens.selector.WalletKindSelectorScreen
import com.tonapps.tonkeeper.Environment
import com.tonapps.tonkeeper.ui.screen.init.InitViewModel
import com.tonapps.tonkeeperx.R
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import ui.theme.MoonTheme
import uikit.base.BaseFragment

class SelectMnemonicTypeScreen : BaseFragment(R.layout.fragment_compose_host) {

    override val fragmentName: String = "SelectMnemonicTypeScreen"

    private val initViewModel: InitViewModel by viewModel(ownerProducer = { requireParentFragment() })
    private val environment: Environment by inject()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<ComposeView>(R.id.compose_view).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MoonTheme(colorScheme = environment.theme) {
                    val items by initViewModel.walletKindsFlow.collectAsState()
                    val selectedKind by initViewModel.selectedWalletKindFlow.collectAsState()
                    val topOffset by initViewModel.uiTopOffset.collectAsState()
                    WalletKindSelectorScreen(
                        items = items,
                        selected = selectedKind,
                        onSelect = initViewModel::selectWalletKind,
                        onContinue = { initViewModel.confirmWalletKind() },
                        modifier = Modifier
                            .padding(top = with(LocalDensity.current) { topOffset.toDp() })
                            .navigationBarsPadding(),
                    )
                }
            }
        }
    }

    companion object {
        fun newInstance() = SelectMnemonicTypeScreen()
    }
}
