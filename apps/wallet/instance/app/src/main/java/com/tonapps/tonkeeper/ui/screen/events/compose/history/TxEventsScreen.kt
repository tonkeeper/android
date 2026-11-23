package com.tonapps.tonkeeper.ui.screen.events.compose.history

import android.os.Bundle
import android.view.View
import androidx.compose.runtime.Composable
import com.tonapps.bus.screenrender.ScreenRenderId
import com.tonapps.core.screenrender.ScreenRenderFragmentLifecycle
import com.tonapps.tonkeeper.koin.walletViewModel
import com.tonapps.tonkeeper.ui.base.compose.ComposeWalletScreen
import com.tonapps.tonkeeper.ui.screen.events.compose.history.ui.TxEventComposable
import com.tonapps.blockchain.model.legacy.WalletEntity
import uikit.base.BaseFragment

// SwipeBack so the history screen can be dragged away like the other stacked screens —
// without it there is no swipe-to-go-back at all on button-navigation devices.
class TxEventsScreen(wallet: WalletEntity) : ComposeWalletScreen(wallet), BaseFragment.SwipeBack {

    override val viewModel: TxEventsViewModel by walletViewModel()

    private val canGoBack: Boolean by lazy { requireArguments().getBoolean(ARG_CAN_GO_BACK) }

    // Several history screens can be stacked; the ViewModel, which reports the stages, owns the session.
    private val screenRender = ScreenRenderFragmentLifecycle(this, ScreenRenderId.History) { viewModel }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        screenRender.onViewCreated()
    }

    @Composable
    override fun ScreenContent() = TxEventComposable(viewModel, canGoBack = canGoBack, onBack = { finish() })

    companion object {
        private const val ARG_CAN_GO_BACK = "can_go_back"

        fun newInstance(wallet: WalletEntity, canGoBack: Boolean = true): TxEventsScreen {
            val fragment = TxEventsScreen(wallet)
            fragment.putBooleanArg(ARG_CAN_GO_BACK, canGoBack)
            return fragment
        }
    }
}
