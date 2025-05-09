package com.tonapps.tonkeeper.ui.screen.browser.share

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.core.net.toUri
import com.tonapps.extensions.getParcelableCompat
import com.tonapps.tonkeeper.extensions.copyToClipboard
import com.tonapps.tonkeeper.extensions.toast
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.ui.base.compose.ComposeWalletScreen
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.dapps.entities.AppEntity
import com.tonapps.wallet.localization.Localization
import org.koin.androidx.viewmodel.ext.android.viewModel
import uikit.base.BaseFragment

class DAppShareScreen(wallet: WalletEntity) : ComposeWalletScreen(wallet),
    BaseFragment.Modal {
    override val fragmentName: String = "DAppShareScreen"

    override val viewModel: BaseWalletVM.EmptyViewViewModel by viewModel()

    private val app: AppEntity
        get() = arguments?.getParcelableCompat(ARG_APP)!!

    private val url: Uri
        get() = "https://app.tonkeeper.com/dapp/${Uri.encode(arguments?.getString(ARG_URL)!!)}".toUri()

    private fun shareLink() {
        val sendIntent = Intent(Intent.ACTION_SEND)
        sendIntent.putExtra(Intent.EXTRA_TEXT, url.toString())
        sendIntent.type = "text/plain"
        val shareIntent = Intent.createChooser(sendIntent, null)
        startActivity(shareIntent)
    }

    private fun copyLink() {
        navigation?.toast(getString(Localization.copied))
        context?.copyToClipboard(url)
    }

    @Composable
    override fun ScreenContent() {
        DAppShareComposable(
            url = url,
            icon = app.iconUrl.toUri(),
            name = app.name,
            onCopy = ::copyLink,
            onShare = ::shareLink,
            onFinishClick = { finish() }
        )
    }

    companion object {
        private const val ARG_APP = "app"
        private const val ARG_URL = "url"

        fun newInstance(wallet: WalletEntity, app: AppEntity, url: Uri): BaseFragment {
            val screen = DAppShareScreen(wallet)
            screen.putParcelableArg(ARG_APP, app)
            screen.putStringArg(ARG_URL, url.toString())
            return screen
        }
    }
}