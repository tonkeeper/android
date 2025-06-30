package com.tonapps.tonkeeper.ui.screen.support

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.core.net.toUri
import com.tonapps.extensions.appVersionCode
import com.tonapps.tonkeeper.extensions.isLightTheme
import com.tonapps.tonkeeper.koin.api
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.ui.base.compose.ComposeWalletScreen
import com.tonapps.wallet.data.account.entities.WalletEntity
import org.koin.androidx.viewmodel.ext.android.viewModel
import uikit.base.BaseFragment

class SupportScreen(wallet: WalletEntity) : ComposeWalletScreen(wallet), BaseFragment.Modal {

    override val fragmentName: String = "SupportScreen"

    override val viewModel: BaseWalletVM.EmptyViewViewModel by viewModel()

    private fun getSupportUrl(): String {
        val startParams = "android${Build.VERSION.SDK_INT}app${requireContext().appVersionCode}"
        val builder = requireContext().api?.config?.directSupportUrl?.toUri()?.buildUpon() ?: return ""
        builder.appendQueryParameter("start", startParams)
        return builder.toString()
    }

    @Composable
    override fun ScreenContent() {
        SupportComposable(
            isLightTheme = requireContext().isLightTheme,
            onButtonClick = {
                navigation?.openURL(getSupportUrl())
                finish()
            },
            onCloseClick = { finish() },
        )
    }

    companion object {
        fun newInstance(wallet: WalletEntity): SupportScreen {
            return SupportScreen(wallet)
        }
    }
}