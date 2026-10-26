package com.tonapps.deposit.multicoin

import android.os.Bundle
import android.view.View
import com.tonapps.bus.core.contract.UtmParameters
import com.tonapps.bus.generated.Events.DepositFlow.DepositFlowFrom
import com.tonapps.core.ComposableFragment
import com.tonapps.deposit.DepositFragment
import com.tonapps.deposit.multicoin.analytics.depositFlowFromOrNull
import com.tonapps.deposit.screens.ramp.RampType
import com.tonapps.extensions.getParcelableCompat
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

        val analyticsFrom = requireNotNull(depositFlowFromOrNull(arguments?.getString(ARG_FROM))) {
            "DepositMulticoinFragment requires $ARG_FROM — create it via create(analyticsFrom)"
        }
        val utm = arguments?.getParcelableCompat<UtmParameters>(ARG_UTM) ?: UtmParameters.Empty

        setContent {
            DepositMulticoinRouter(
                initial = startRoute,
                rampType = rampType,
                analyticsFrom = analyticsFrom,
                utm = utm,
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
        private const val ARG_UTM = "arg_utm"

        fun create(
            rampType: RampType = RampType.RampOn,
            initial: DepositMulticoinRoutes? = null,
            analyticsFrom: DepositFlowFrom,
            utm: UtmParameters = UtmParameters.Empty,
        ): DepositMulticoinFragment {
            return DepositMulticoinFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_RAMP_TYPE, rampType.name)
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
