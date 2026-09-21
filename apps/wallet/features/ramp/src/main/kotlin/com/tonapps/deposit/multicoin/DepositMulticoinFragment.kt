package com.tonapps.deposit.multicoin

import android.os.Bundle
import android.view.View
import com.tonapps.bus.generated.Events.DepositFlow.DepositFlowFrom
import com.tonapps.core.ComposableFragment
import com.tonapps.deposit.DepositFragment
import com.tonapps.deposit.screens.ramp.RampType
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import uikit.base.BaseFragment
import uikit.extensions.activity

class DepositMulticoinFragment : ComposableFragment(), BaseFragment.BottomSheet {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val delegate = context?.activity as? DepositFragment.Delegate

        val startRoute = arguments?.getString(ARG_INITIAL)
            ?.let { Json.decodeFromString<DepositMulticoinRoutes>(it) }

        val rampType = arguments?.getString(ARG_RAMP_TYPE)
            ?.let { runCatching { RampType.valueOf(it) }.getOrNull() }
            ?: RampType.RampOn

        val analyticsFrom = arguments?.getString(ARG_FROM)
            ?.let { key -> DepositFlowFrom.entries.firstOrNull { it.key == key } }
            ?: DepositFlowFrom.WalletScreen

        setContent {
            DepositMulticoinRouter(
                initial = startRoute,
                rampType = rampType,
                analyticsFrom = analyticsFrom,
                onBack = { finish() },
                onOpenWidget = { url ->
                    finish()
                    delegate?.onOpenProvider(url)
                },
            )
        }
    }

    companion object {
        private const val ARG_INITIAL = "arg_initial"
        private const val ARG_RAMP_TYPE = "arg_ramp_type"
        private const val ARG_FROM = "arg_from"

        fun create(
            rampType: RampType = RampType.RampOn,
            initial: DepositMulticoinRoutes? = null,
            analyticsFrom: DepositFlowFrom = DepositFlowFrom.WalletScreen,
        ): DepositMulticoinFragment {
            return DepositMulticoinFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_RAMP_TYPE, rampType.name)
                    putString(ARG_FROM, analyticsFrom.key)
                    initial?.let {
                        putString(ARG_INITIAL, Json.encodeToString(initial))
                    }
                }
            }
        }
    }
}
