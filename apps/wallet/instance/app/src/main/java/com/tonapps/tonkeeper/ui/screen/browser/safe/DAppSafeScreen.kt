package com.tonapps.tonkeeper.ui.screen.browser.safe

import androidx.compose.runtime.Composable
import com.tonapps.tonkeeper.helper.BrowserHelper
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.ui.base.compose.ComposeWalletScreen
import com.tonapps.wallet.data.account.entities.WalletEntity
import org.koin.androidx.viewmodel.ext.android.viewModel
import uikit.base.BaseFragment

class DAppSafeScreen(wallet: WalletEntity) : ComposeWalletScreen(wallet), BaseFragment.Modal {

    override val fragmentName: String = "DAppSafeScreen"

    override val viewModel: BaseWalletVM.EmptyViewViewModel by viewModel()

    @Composable
    override fun ScreenContent() {
        DAppSafeComposable(
            onSafeClick = {
                BrowserHelper.open(requireActivity(), "https://tonkeeper.helpscoutdocs.com/")
            },
            onClose = { finish() }
        )
    }

    companion object {
        fun newInstance(wallet: WalletEntity) = DAppSafeScreen(wallet)
    }
}