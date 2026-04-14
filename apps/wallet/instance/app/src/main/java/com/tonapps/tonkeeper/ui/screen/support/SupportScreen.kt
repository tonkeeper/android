package com.tonapps.tonkeeper.ui.screen.support

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.core.net.toUri
import com.tonapps.extensions.appVersionCode
import com.tonapps.tonkeeper.koin.api
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.ui.base.compose.ComposeWalletScreen
import com.tonapps.blockchain.model.legacy.WalletEntity
import org.koin.androidx.viewmodel.ext.android.viewModel
import uikit.base.BaseFragment

class SupportScreen(wallet: WalletEntity) : ComposeWalletScreen(wallet), BaseFragment.Modal {

    override val fragmentName: String = "SupportScreen"

    override val viewModel: BaseWalletVM.EmptyViewViewModel by viewModel()

    private fun getContactUrl(): String {
        return requireContext().api?.getConfig(wallet.network)?.supportLink.orEmpty()
    }

    private fun getSupportUrl(): String {
        val startParams = "android${Build.VERSION.SDK_INT}app${requireContext().appVersionCode}"
        val builder = requireContext().api?.getConfig(wallet.network)?.directSupportUrl?.toUri()?.buildUpon() ?: return ""
        builder.appendQueryParameter("start", startParams)
        return builder.toString()
    }

    @Composable
    override fun ScreenContent() {
        SupportComposable(
            onTelegramClick = {
                navigation?.openURL(getSupportUrl())
                finish()
            },
            onEmailClick = {
                navigation?.openURL(getContactUrl())
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
