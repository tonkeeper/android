package com.tonapps.deposit.multicoin.screens.qr

import android.os.Bundle
import android.view.View
import com.tonapps.bus.generated.Events.DepositFlow.DepositFlowFrom
import com.tonapps.core.ComposableFragment
import com.tonapps.deposit.multicoin.DepositMulticoinFragment
import com.tonapps.deposit.multicoin.DepositMulticoinRoutes
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import uikit.base.BaseFragment
import uikit.navigation.Navigation.Companion.navigation

// A standalone bottom sheet showing the receiving QR of a single asset's account. Unlike the
// receive flow inside DepositMulticoinFragment, there is no address list underneath: the QR is
// the sheet, so closing it lands back on the caller (e.g. the asset screen).
class ReceiveQrFragment : ComposableFragment(), BaseFragment.BottomSheet {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val assetId = requireNotNull(requireArguments().getString(ARG_ASSET_ID)) {
            "ReceiveQrFragment requires $ARG_ASSET_ID — create it via create(assetId)"
        }

        val analyticsFrom = requireArguments().getString(ARG_FROM)
            ?.let { key -> DepositFlowFrom.entries.firstOrNull { it.key == key } }
            ?: DepositFlowFrom.JettonScreen

        setContent {
            val feature = koinViewModel<ReceiveQrFeature> {
                parametersOf(ReceiveQrData(assetId = assetId, analyticsFrom = analyticsFrom))
            }
            ReceiveQrScreen(
                feature = feature,
                onFallbackToList = {
                    navigation?.add(
                        DepositMulticoinFragment.create(
                            initial = DepositMulticoinRoutes.Receive,
                            analyticsFrom = analyticsFrom,
                        )
                    )
                    finish()
                },
                onClose = { finish() },
            )
        }
    }

    companion object {
        private const val ARG_ASSET_ID = "asset_id"
        private const val ARG_FROM = "arg_from"

        fun create(
            assetId: String,
            analyticsFrom: DepositFlowFrom = DepositFlowFrom.JettonScreen,
        ): ReceiveQrFragment {
            return ReceiveQrFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_ASSET_ID, assetId)
                    putString(ARG_FROM, analyticsFrom.key)
                }
            }
        }
    }
}
