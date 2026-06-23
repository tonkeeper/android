package com.tonapps.dapp.warning

import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.core.net.toUri
import com.tonapps.core.ComposableFragment
import com.tonapps.extensions.getParcelableCompat
import com.tonapps.wallet.data.dapps.entities.AppEntity
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import uikit.base.BaseFragment

class DAppConfirmFragment : ComposableFragment(), BaseFragment.Modal {

    interface Delegate {
        fun openDapp(walletId: String, app: AppEntity, dAppUrl: Uri, source: String)
    }

    override val fragmentName: String = "DAppShareScreen"

    private val walletId: String
        get() = requireArguments().getParcelableCompat(ARG_WALLET)!!

    private val app: AppEntity
        get() = requireArguments().getParcelableCompat(ARG_APP)!!

    private val dAppUrl: Uri
        get() = requireArguments().getString(ARG_URL)!!.toUri()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val data = DAppConfirmData(
            walletId = walletId,
            app = app,
            dAppUrl = dAppUrl
        )

        setContent {
            val viewModel = koinViewModel<DAppConfirmFeature> {
                parametersOf(data)
            }

            DAppConfirmScreen(
                viewModel = viewModel,
                onOpen = { openDApp() },
                onFinishClick = { finish() }
            )
        }
    }

    private fun openDApp() {
        context?.let {
            if (it is Delegate) {
                it.openDapp(
                    walletId = walletId,
                    app = app,
                    dAppUrl = dAppUrl,
                    source = "deep-link"
                )
            }
        }

        finish()
    }

    companion object {
        private const val ARG_WALLET = "wallet"
        private const val ARG_APP = "app"
        private const val ARG_URL = "url"

        fun newInstance(walletId: String, app: AppEntity, url: Uri): BaseFragment {
            val screen = DAppConfirmFragment()
            screen.putStringArg(ARG_WALLET, walletId)
            screen.putParcelableArg(ARG_APP, app)
            screen.putStringArg(ARG_URL, url.toString())
            return screen
        }
    }
}
