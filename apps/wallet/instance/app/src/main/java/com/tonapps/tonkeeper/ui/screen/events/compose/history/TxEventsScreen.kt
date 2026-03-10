package com.tonapps.tonkeeper.ui.screen.events.compose.history

import android.os.Bundle
import android.view.View
import androidx.compose.runtime.Composable
import com.tonapps.tonkeeper.koin.walletViewModel
import com.tonapps.tonkeeper.ui.base.compose.ComposeWalletScreen
import com.tonapps.tonkeeper.ui.screen.events.compose.history.ui.TxEventComposable
import com.tonapps.wallet.data.account.entities.WalletEntity

class TxEventsScreen(wallet: WalletEntity) : ComposeWalletScreen(wallet) {

    override val viewModel: TxEventsViewModel by walletViewModel()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
    }

    @Composable
    override fun ScreenContent() = TxEventComposable(viewModel)

    companion object {

        fun newInstance(wallet: WalletEntity) = TxEventsScreen(wallet)
    }
}