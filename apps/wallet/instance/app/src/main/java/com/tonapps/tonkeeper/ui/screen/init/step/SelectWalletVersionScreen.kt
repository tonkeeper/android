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
import com.tonapps.onboading.screens.selector.WalletSelectorScreen
import com.tonapps.tonkeeper.Environment
import com.tonapps.tonkeeper.ui.screen.init.InitViewModel
import com.tonapps.tonkeeperx.R
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import ui.theme.MoonTheme
import uikit.base.BaseFragment

class SelectWalletVersionScreen : BaseFragment(R.layout.fragment_compose_host) {

    override val fragmentName: String = "SelectWalletVersionScreen"

    private val initViewModel: InitViewModel by viewModel(ownerProducer = { requireParentFragment() })
    private val environment: Environment by inject()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<ComposeView>(R.id.compose_view).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MoonTheme(colorScheme = environment.theme) {
                    val accounts by initViewModel.walletAccountsFlow.collectAsState()
                    val selectedAddress by initViewModel.selectedAddressFlow.collectAsState()
                    val topOffset by initViewModel.uiTopOffset.collectAsState()
                    WalletSelectorScreen(
                        accounts = accounts,
                        selectedAddress = selectedAddress,
                        onSelect = initViewModel::selectWalletAccount,
                        onContinue = { initViewModel.confirmWalletVersion() },
                        modifier = Modifier
                            .padding(top = with(LocalDensity.current) { topOffset.toDp() })
                            .navigationBarsPadding(),
                    )
                }
            }
        }
    }

    companion object {
        fun newInstance() = SelectWalletVersionScreen()
    }
}
