package com.tonapps.deposit.screens.qr

import android.os.Build
import android.os.Bundle
import android.view.View
import com.tonapps.blockchain.model.legacy.TokenEntity
import com.tonapps.bus.core.contract.UtmParameters
import com.tonapps.bus.generated.Events.DepositFlow.DepositFlowFrom
import com.tonapps.core.ComposableFragment
import com.tonapps.deposit.multicoin.analytics.depositFlowFromOrNull
import com.tonapps.extensions.getParcelableCompat
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import uikit.base.BaseFragment

class QrAssetFragment: ComposableFragment(), BaseFragment.BottomSheet {

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
            val analyticsFrom = requireNotNull(depositFlowFromOrNull(getString(ARG_FROM))) {
                "QrAssetFragment requires $ARG_FROM — create it via newInstance(analyticsFrom)"
            }
            return QrAssetData(
                token = token,
                withBuyButton = withBuy,
                walletId = walletId,
                analyticsFrom = analyticsFrom,
                utm = getParcelableCompat<UtmParameters>(ARG_UTM) ?: UtmParameters.Empty,
            )
        }
    }

    companion object Companion {
        // TODO to assetId
        private const val ARG_TOKEN = "token"
        private const val ARG_WITH_BUY = "with_buy"
        private const val ARG_WALLET_ID = "wallet_id"
        private const val ARG_FROM = "from"
        private const val ARG_UTM = "utm"

        fun newInstance(
            token: TokenEntity? = null,
            withBuyButton: Boolean = false,
            walletId: String? = null,
            analyticsFrom: DepositFlowFrom,
            utm: UtmParameters = UtmParameters.Empty,
        ): BaseFragment {
            val screen = QrAssetFragment()
            screen.putStringArg(ARG_FROM, analyticsFrom.key)
            screen.putParcelableArg(ARG_UTM, utm)
            token?.let { screen.putParcelableArg(ARG_TOKEN, it) }
            screen.putBooleanArg(ARG_WITH_BUY, withBuyButton)
            walletId?.let { screen.putStringArg(ARG_WALLET_ID, it) }
            return screen
        }
    }
}
