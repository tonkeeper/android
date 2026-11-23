package com.tonapps.deposit.multicoin

import android.os.Bundle
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tonapps.bus.core.contract.UtmParameters
import com.tonapps.bus.generated.Events.DepositFlow.DepositFlowFrom
import com.tonapps.core.ComposableFragment
import com.tonapps.deposit.DepositFragment
import com.tonapps.deposit.multicoin.analytics.depositFlowFromOrNull
import com.tonapps.deposit.multicoin.screens.buy.BuyCryptoAction
import com.tonapps.deposit.multicoin.screens.moonpay.MoonPayPaymentFragment
import com.tonapps.deposit.multicoin.screens.moonpay.MoonPayPaymentRetry
import com.tonapps.deposit.screens.ramp.RampType
import com.tonapps.extensions.getParcelableCompat
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import uikit.base.BaseFragment
import uikit.extensions.activity
import uikit.navigation.Navigation.Companion.navigation
import java.util.UUID

class DepositMulticoinFragment : ComposableFragment(), BaseFragment.BottomSheet {

    private var paymentEnded by mutableStateOf<BuyCryptoAction.PaymentEnded?>(null)

    // Results go through the activity: another deposit opened by a deeplink must not take this one's answer.
    private lateinit var paymentResultKey: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        paymentResultKey = savedInstanceState?.getString(STATE_PAYMENT_RESULT_KEY)
            ?: "moonpay_payment_status_${UUID.randomUUID()}"
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_PAYMENT_RESULT_KEY, paymentResultKey)
    }

    override fun onDestroy() {
        if (isRemoving) {
            navigation?.resetFragmentResult(paymentResultKey)
        }
        super.onDestroy()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        navigation?.setFragmentResultListener(paymentResultKey) { result ->
            if (result.getBoolean(MoonPayPaymentFragment.RESULT_CLOSE)) {
                finish()
            } else {
                val retry = result.getString(MoonPayPaymentFragment.RESULT_RETRY)
                    ?.let { name -> MoonPayPaymentRetry.entries.firstOrNull { it.name == name } }
                paymentEnded = BuyCryptoAction.PaymentEnded(retry)
            }
        }

        val delegate = context?.activity as? DepositFragment.Delegate

        val walletId = requireNotNull(arguments?.getString(ARG_WALLET_ID)) {
            "DepositMulticoinFragment requires $ARG_WALLET_ID — create it via create(walletId)"
        }

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
                flowOwner = this,
                walletId = walletId,
                initial = startRoute,
                rampType = rampType,
                analyticsFrom = analyticsFrom,
                utm = utm,
                onBack = { finish() },
                onOpenWidget = { url ->
                    finish()
                    delegate?.onOpenProvider(url)
                },
                onOpenMoonPay = { data -> navigation?.add(MoonPayPaymentFragment.newInstance(data, paymentResultKey)) },
                paymentEnded = paymentEnded,
                onPaymentEndedTaken = { paymentEnded = null },
            )
        }
    }

    companion object {
        private const val ARG_WALLET_ID = "arg_wallet_id"
        private const val ARG_INITIAL = "arg_initial"
        private const val ARG_RAMP_TYPE = "arg_ramp_type"
        private const val ARG_FROM = "arg_from"
        private const val ARG_UTM = "arg_utm"
        private const val STATE_PAYMENT_RESULT_KEY = "payment_result_key"

        fun create(
            walletId: String,
            rampType: RampType = RampType.RampOn,
            initial: DepositMulticoinRoutes? = null,
            analyticsFrom: DepositFlowFrom,
            utm: UtmParameters = UtmParameters.Empty,
        ): DepositMulticoinFragment {
            return DepositMulticoinFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_WALLET_ID, walletId)
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
