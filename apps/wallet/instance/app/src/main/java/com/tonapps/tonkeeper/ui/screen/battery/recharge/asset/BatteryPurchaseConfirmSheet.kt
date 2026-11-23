package com.tonapps.tonkeeper.ui.screen.battery.recharge.asset

import android.os.Bundle
import android.view.View
import com.tonapps.blockchain.model.ConfirmRequest
import com.tonapps.core.ComposableFragment
import com.tonapps.deposit.multicoin.screens.confirm.ConfirmScreen
import com.tonapps.core.navigation.NavigationDelegate
import kotlinx.serialization.json.Json
import uikit.base.BaseFragment
import uikit.extensions.activity
import uikit.extensions.setDraggable
import uikit.widget.BottomSheetLayout
import uikit.navigation.Navigation.Companion.navigation

/** Hosts the confirm screen of a battery purchase and tells the recharge screen under [ARG_REQUEST_KEY] once it is sent. */
class BatteryPurchaseConfirmSheet : ComposableFragment(), BaseFragment.BottomSheet {

    override val fragmentName: String = "BatteryPurchaseConfirmSheet"

    // While a purchase is being sent the sheet stays: its result tells the recharge screen to refresh.
    private var isSending = false

    // A back gesture's preview would move the sheet that this back then keeps: no preview while sending.
    override val predictiveBackPreviewEnabled: Boolean
        get() = !isSending && super.predictiveBackPreviewEnabled

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val args = requireArguments()
        val request = Json.decodeFromString<ConfirmRequest>(args.getString(ARG_REQUEST)!!)
        val requestKey = args.getString(ARG_REQUEST_KEY)!!
        val delegate = context?.activity as? NavigationDelegate

        setContent {
            ConfirmScreen(
                request = request,
                onClose = { finish() },
                onBack = null,
                onSendSuccess = {
                    navigation?.setFragmentResult(requestKey, Bundle())
                    finish()
                },
                onOpenBattery = { walletId, from -> delegate?.onOpenBattery(walletId = walletId, from = from) },
                onTopUp = {},
                onBusyChange = ::setSending,
            )
        }
    }

    private fun setSending(sending: Boolean) {
        isSending = sending
        (view as? BottomSheetLayout)?.setDraggable(!sending)
    }

    override fun onBackPressed(): Boolean {
        if (isSending) {
            return false
        }
        return super.onBackPressed()
    }

    companion object {
        private const val ARG_REQUEST = "request"
        private const val ARG_REQUEST_KEY = "request_key"

        fun newInstance(request: ConfirmRequest, requestKey: String): BaseFragment {
            val fragment = BatteryPurchaseConfirmSheet()
            fragment.putStringArg(ARG_REQUEST, Json.encodeToString(request))
            fragment.putStringArg(ARG_REQUEST_KEY, requestKey)
            return fragment
        }
    }
}
