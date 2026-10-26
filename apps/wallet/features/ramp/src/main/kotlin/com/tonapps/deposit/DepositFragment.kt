package com.tonapps.deposit

import android.os.Bundle
import android.view.View
import com.tonapps.bus.core.contract.UtmParameters
import com.tonapps.bus.generated.Events.DepositFlow.DepositFlowFrom
import com.tonapps.core.ComposableFragment
import com.tonapps.deposit.multicoin.analytics.depositFlowFromOrNull
import com.tonapps.extensions.getParcelableCompat
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import uikit.base.BaseFragment
import uikit.extensions.activity

class DepositFragment : ComposableFragment(), BaseFragment.BottomSheet {

    interface Delegate {
        fun onOpenProvider(url: String)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val delegate = context?.activity as? Delegate

        val startRoute = arguments?.getString(ARG_INITIAL)
            ?.let { Json.decodeFromString<DepositRoutes>(it) }
            ?: DepositRoutes.Ramp

        val analyticsFrom = requireNotNull(depositFlowFromOrNull(arguments?.getString(ARG_FROM))) {
            "DepositFragment requires $ARG_FROM — create it via create(analyticsFrom)"
        }
        val utm = arguments?.getParcelableCompat<UtmParameters>(ARG_UTM) ?: UtmParameters.Empty

        setContent {
            DepositRouter(
                initial = startRoute,
                walletId = arguments?.getString(ARG_WALLET_ID),
                analyticsFrom = analyticsFrom,
                utm = utm,
                onBack = { finish() },
                openProvider = {
                    finish()
                    delegate?.onOpenProvider(it)
                },
            )
        }
    }

    companion object {
        private const val ARG_INITIAL = "arg_initial"
        private const val ARG_WALLET_ID = "arg_wallet_id"
        private const val ARG_FROM = "arg_from"
        private const val ARG_UTM = "arg_utm"

        fun create(
            initial: DepositRoutes? = null,
            walletId: String? = null,
            analyticsFrom: DepositFlowFrom,
            utm: UtmParameters = UtmParameters.Empty,
        ): DepositFragment {
            return DepositFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_FROM, analyticsFrom.key)
                    putParcelable(ARG_UTM, utm)
                    initial?.let {
                        putString(ARG_INITIAL, Json.encodeToString(initial))
                    }
                    walletId?.let {
                        putString(ARG_WALLET_ID, it)
                    }
                }
            }
        }
    }
}
