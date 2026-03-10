package com.tonapps.tonkeeper.ui.screen.browser.confirm

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.core.net.toUri
import com.tonapps.extensions.getParcelableCompat
import com.tonapps.tonkeeper.extensions.copyToClipboard
import com.tonapps.tonkeeper.extensions.toast
import com.tonapps.tonkeeper.koin.settingsRepository
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.ui.base.compose.ComposeWalletScreen
import com.tonapps.tonkeeper.ui.screen.browser.dapp.DAppScreen
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.dapps.entities.AppEntity
import com.tonapps.wallet.localization.Localization
import org.koin.androidx.viewmodel.ext.android.viewModel
import uikit.base.BaseFragment

class DAppConfirmScreen(wallet: WalletEntity) : ComposeWalletScreen(wallet),
    BaseFragment.Modal {
    override val fragmentName: String = "DAppShareScreen"

    override val viewModel: BaseWalletVM.EmptyViewViewModel by viewModel()

    private val app: AppEntity
        get() = arguments?.getParcelableCompat(ARG_APP)!!

    private val dAppUrl: Uri
        get() = arguments?.getString(ARG_URL)!!.toUri()

    private fun openDApp() {
        navigation?.add(DAppScreen.newInstance(
            wallet = wallet,
            title = app.name,
            url = dAppUrl,
            iconUrl = app.iconUrl,
            source = "deep-link",
        ))
        finish()
    }

    @Composable
    override fun ScreenContent() {
        DAppConfirmComposable(
            host = dAppUrl.host ?: app.host,
            icon = app.iconUrl.toUri(),
            name = app.name,
            onOpen = ::openDApp,
            onCheckedChange = { checked ->
                context?.settingsRepository?.setDAppOpenConfirm(wallet.id, app.host, !checked)
            },
            onFinishClick = { finish() }
        )
    }

    companion object {
        private const val ARG_APP = "app"
        private const val ARG_URL = "url"

        fun newInstance(wallet: WalletEntity, app: AppEntity, url: Uri): BaseFragment {
            val screen = DAppConfirmScreen(wallet)
            screen.putParcelableArg(ARG_APP, app)
            screen.putStringArg(ARG_URL, url.toString())
            return screen
        }
    }
}