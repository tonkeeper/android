package com.tonapps.deposit.multicoin

import android.os.Bundle
import android.view.View
import com.tonapps.bus.generated.Events.WithdrawFlow.WithdrawFlowFrom
import com.tonapps.core.ComposableFragment
import com.tonapps.core.navigation.NavigationDelegate
import com.tonapps.deposit.multicoin.analytics.WithdrawAnalytics
import com.tonapps.deposit.screens.ramp.RampType
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

        val analyticsFrom = WithdrawAnalytics.fromKeyOrDefault(arguments?.getString(ARG_FROM))

        setContent {
            WithdrawMulticoinRouter(
                initial = startRoute,
                onBack = { finish() },
                onTopUp = { assetId ->
                    navigation?.add(
                        DepositMulticoinFragment.create(
                            rampType = RampType.RampOn,
                            initial = DepositMulticoinRoutes.Buy(assetId),
                        )
                    )
                },
                onOpenBattery = { walletId, from ->
                    delegate?.onOpenBattery(walletId = walletId, from = from)
                },
                analyticsFrom = analyticsFrom,
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

        fun create(
            initial: WithdrawMulticoinRoutes? = null,
            analyticsFrom: WithdrawFlowFrom = WithdrawFlowFrom.WalletScreen,
        ): WithdrawMulticoinFragment {
            return WithdrawMulticoinFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_FROM, analyticsFrom.key)
                    initial?.let {
                        putString(ARG_INITIAL, Json.encodeToString(initial))
                    }
                }
            }
        }
    }
}
