package com.tonapps.deposit

import android.os.Bundle
import android.view.View
import com.tonapps.bus.core.contract.UtmParameters
import com.tonapps.bus.generated.Events.DepositFlow.DepositFlowFrom
import com.tonapps.bus.generated.Events.WithdrawFlow.WithdrawFlowFrom
import com.tonapps.core.ComposableFragment
import com.tonapps.deposit.multicoin.analytics.asDepositFlowFrom
import com.tonapps.deposit.multicoin.analytics.withdrawFlowFromOrNull
import com.tonapps.extensions.getParcelableCompat
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import uikit.base.BaseFragment
import uikit.extensions.activity
import uikit.navigation.Navigation.Companion.navigation

class WithdrawFragment: ComposableFragment(), BaseFragment.BottomSheet {

    interface Delegate {
        fun onOpenProvider(url: String)
        fun onShowError(message: String)
        fun onOpenAddressBook(onResult: (String) -> Unit)
        fun onBuyTon(from: DepositFlowFrom) {}
        fun onGetTrx(from: DepositFlowFrom) {}
        fun onRechargeBattery() {}
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val delegate = context?.activity as? Delegate

        val startRoute = requireArguments().getString(ARG_INITIAL)
            ?.let { Json.decodeFromString<WithdrawRoutes>(it) }
            ?: WithdrawRoutes.Ramp

        val analyticsFrom = requireNotNull(withdrawFlowFromOrNull(requireArguments().getString(ARG_FROM))) {
            "WithdrawFragment requires $ARG_FROM — create it via create(analyticsFrom)"
        }
        val utm = requireArguments().getParcelableCompat<UtmParameters>(ARG_UTM) ?: UtmParameters.Empty

        setContent {
            WithdrawRouter(
                initial = startRoute,
                analyticsFrom = analyticsFrom,
                utm = utm,
                onBack = { finish() },
                onSendSuccess = {
                    navigation?.openURL("tonkeeper://activity?from=send")
                    finish()
                },
                openProvider = {
                    finish()
                    delegate?.onOpenProvider(it)
                },
                onAddressBook = delegate?.let { d ->
                    { onResult -> d.onOpenAddressBook(onResult) }
                },
                onShowError = {
                    delegate?.onShowError(it)
                },
                onBuyTon = { delegate?.onBuyTon(analyticsFrom.asDepositFlowFrom()) },
                onGetTrx = { delegate?.onGetTrx(analyticsFrom.asDepositFlowFrom()) },
                onRechargeBattery = { delegate?.onRechargeBattery() },
            )
        }
    }

    companion object {
        private const val ARG_INITIAL = "arg_initial"
        private const val ARG_FROM = "arg_from"
        private const val ARG_UTM = "arg_utm"

        fun create(
            initial: WithdrawRoutes? = null,
            analyticsFrom: WithdrawFlowFrom,
            utm: UtmParameters = UtmParameters.Empty,
        ): WithdrawFragment {
            return WithdrawFragment().apply {
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
