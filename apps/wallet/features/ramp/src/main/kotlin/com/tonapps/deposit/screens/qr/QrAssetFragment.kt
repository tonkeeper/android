package com.tonapps.deposit.screens.qr

import android.os.Build
import android.os.Bundle
import android.view.View
import com.tonapps.blockchain.model.legacy.TokenEntity
import com.tonapps.core.ComposableFragment
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import uikit.base.BaseFragment

class QrAssetFragment private constructor(): ComposableFragment(), BaseFragment.BottomSheet {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val data = getData()
        setContent {
            val viewModel = koinViewModel<QrAssetFeature> {
                parametersOf(data)
            }

            QrScreen(
                viewModel = viewModel,
                showBuyButton = data.withBuyButton,
                onFinishClick = { finish() },
                onBuyClick = {},
            )
        }
    }

    private fun getData(): QrAssetData {
        requireArguments().apply {
            val token = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                getParcelable(ARG_TOKEN, TokenEntity::class.java)
            } else {
                @Suppress("DEPRECATION")
                getParcelable(ARG_TOKEN)
            }
            val withBuy = getBoolean(ARG_WITH_BUY)
            val walletId = getString(ARG_WALLET_ID)
            return QrAssetData(
                token = token,
                withBuyButton = withBuy,
                walletId = walletId,
            )
        }
    }

    companion object Companion {
        // TODO to assetId
        private const val ARG_TOKEN = "token"
        private const val ARG_WITH_BUY = "with_buy"
        private const val ARG_WALLET_ID = "wallet_id"

        fun newInstance(
            token: TokenEntity? = null,
            withBuyButton: Boolean = false,
            walletId: String? = null,
        ): BaseFragment {
            val screen = QrAssetFragment()
            token?.let { screen.putParcelableArg(ARG_TOKEN, it) }
            screen.putBooleanArg(ARG_WITH_BUY, withBuyButton)
            walletId?.let { screen.putStringArg(ARG_WALLET_ID, it) }
            return screen
        }
    }
}
