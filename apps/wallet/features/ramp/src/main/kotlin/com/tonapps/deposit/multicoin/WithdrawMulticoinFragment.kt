package com.tonapps.deposit.multicoin

import android.os.Bundle
import android.view.View
import com.tonapps.bus.core.contract.UtmParameters
import com.tonapps.bus.generated.Events.WithdrawFlow.WithdrawFlowFrom
import com.tonapps.core.ComposableFragment
import com.tonapps.core.navigation.NavigationDelegate
import com.tonapps.deposit.multicoin.analytics.asDepositFlowFrom
import com.tonapps.deposit.multicoin.analytics.withdrawFlowFromOrNull
import com.tonapps.deposit.screens.ramp.RampType
import com.tonapps.extensions.getParcelableCompat
import kotlinx.serialization.json.Json
import uikit.base.BaseFragment
import uikit.extensions.activity
import uikit.navigation.Navigation.Companion.navigation

class WithdrawMulticoinFragment : ComposableFragment(), BaseFragment.BottomSheet {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val delegate = context?.activity as? NavigationDelegate

        val startRoute = arguments?.getString(ARG_INITIAL)
            ?.let { Json.decodeFromString<WithdrawMulticoinRoutes>(it) }
            ?: WithdrawMulticoinRoutes.Picker()

        val analyticsFrom = requireNotNull(withdrawFlowFromOrNull(arguments?.getString(ARG_FROM))) {
            "WithdrawMulticoinFragment requires $ARG_FROM — create it via create(analyticsFrom)"
        }
        val utm = arguments?.getParcelableCompat<UtmParameters>(ARG_UTM) ?: UtmParameters.Empty

        setContent {
            WithdrawMulticoinRouter(
                initial = startRoute,
                onBack = { finish() },
                onTopUp = { assetId ->
                    navigation?.add(
                        DepositMulticoinFragment.create(
                            rampType = RampType.RampOn,
                            initial = DepositMulticoinRoutes.Buy(assetId),
                            analyticsFrom = analyticsFrom.asDepositFlowFrom(),
                        )
                    )
                },
                onOpenBattery = { walletId, from ->
                    delegate?.onOpenBattery(walletId = walletId, from = from)
                },
                analyticsFrom = analyticsFrom,
                utm = utm,
                onSendSuccess = {
                    delegate?.onOpenHistory()
                    finish()
                },
            )
        }
    }

    companion object {
        private const val ARG_INITIAL = "arg_initial"
        private const val ARG_FROM = "arg_from"
        private const val ARG_UTM = "arg_utm"

        fun create(
            initial: WithdrawMulticoinRoutes? = null,
            analyticsFrom: WithdrawFlowFrom,
            utm: UtmParameters = UtmParameters.Empty,
        ): WithdrawMulticoinFragment {
            return WithdrawMulticoinFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_FROM, analyticsFrom.key)
                    putParcelable(ARG_UTM, utm)
                    initial?.let {
                        putString(ARG_INITIAL, Json.encodeToString(initial))
                    }
                }
            }
        }
    }
}
