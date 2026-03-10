package com.tonapps.tonkeeper.ui.screen.watchonly

import androidx.compose.runtime.Composable
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.ui.base.compose.ComposeWalletScreen
import com.tonapps.tonkeeper.ui.screen.init.InitArgs
import com.tonapps.tonkeeper.ui.screen.init.InitScreen
import com.tonapps.wallet.data.account.entities.WalletEntity
import org.koin.androidx.viewmodel.ext.android.viewModel
import uikit.base.BaseFragment

class WatchInfoScreen(wallet: WalletEntity) : ComposeWalletScreen(wallet), BaseFragment.Modal {

    override val fragmentName: String = "WatchInfoScreen"

    override val viewModel: BaseWalletVM.EmptyViewViewModel by viewModel()

    @Composable
    override fun ScreenContent() {
        SupportComposable(
            onRecoveryClick = {
                navigation?.add(InitScreen.newInstance(type = InitArgs.Type.Import, watchRecoveryAccountId = wallet.accountId))
                finish()
            },
            onContinueClick = { finish() },
        )
    }

    companion object {
        fun newInstance(wallet: WalletEntity): WatchInfoScreen {
            return WatchInfoScreen(wallet)
        }
    }
}