package com.tonapps.tonkeeper.ui.screen.events.compose.history

import android.os.Bundle
import android.view.View
import androidx.compose.runtime.Composable
import com.tonapps.tonkeeper.koin.walletViewModel
import com.tonapps.tonkeeper.ui.base.compose.ComposeWalletScreen
import com.tonapps.tonkeeper.ui.screen.events.compose.history.ui.TxEventComposable
import com.tonapps.blockchain.model.legacy.WalletEntity

class TxEventsScreen(wallet: WalletEntity, private val canGoBack: Boolean) : ComposeWalletScreen(wallet) {

    override val viewModel: TxEventsViewModel by walletViewModel()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
    }

    @Composable
    override fun ScreenContent() = TxEventComposable(viewModel, canGoBack = canGoBack, onBack = { finish() })

    companion object {
        fun newInstance(wallet: WalletEntity, canGoBack: Boolean = true) = TxEventsScreen(wallet, canGoBack)
    }
}
