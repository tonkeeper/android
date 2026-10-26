package com.tonapps.wallet.features.events.screens

import android.os.Bundle
import android.view.View
import com.tonapps.bus.generated.Events.DepositFlow.DepositFlowFrom
import com.tonapps.core.ComposableFragment
import com.tonapps.core.navigation.NavigationDelegate
import uikit.base.BaseFragment
import uikit.extensions.activity

// SwipeBack so the history screen can be dragged away like the other stacked screens —
// without it there is no swipe-to-go-back at all on button-navigation devices.
class EventsFragment : ComposableFragment(), BaseFragment.SwipeBack {

    override val fragmentName: String = "EventsFragment"

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val delegate = context?.activity as? NavigationDelegate
        val assetId = arguments?.getString(ARG_ASSET_ID)
        val modeName = arguments?.getString(ARG_MODE)
        val mode = EventsScreenMode.entries.firstOrNull { it.name == modeName }
            ?: EventsScreenMode.General
        val awaitsFundingActivity = arguments?.getBoolean(ARG_AWAITS_FUNDING_ACTIVITY) ?: false

        setContent {
            EventsScreen(
                assetId = assetId,
                mode = mode,
                awaitsFundingActivity = awaitsFundingActivity,
                onBack = { finish() },
                onOpenDeposit = { delegate?.onOpenDeposit(DepositFlowFrom.HistoryScreen) },
                onOpenNft = { address -> delegate?.onOpenNft(address) },
            )
        }
    }

    companion object {
        private const val ARG_ASSET_ID = "asset_id"
        private const val ARG_MODE = "mode"
        private const val ARG_AWAITS_FUNDING_ACTIVITY = "awaits_funding_activity"

        fun newInstance(assetId: String? = null) = EventsFragment().apply {
            if (assetId != null) {
                arguments = Bundle().apply {
                    putString(ARG_ASSET_ID, assetId)
                }
            }
        }

        fun perpsHistory(awaitsFundingActivity: Boolean) = EventsFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_MODE, EventsScreenMode.Perps.name)
                putBoolean(ARG_AWAITS_FUNDING_ACTIVITY, awaitsFundingActivity)
            }
        }
    }
}
