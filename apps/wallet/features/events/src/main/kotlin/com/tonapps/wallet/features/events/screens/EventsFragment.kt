package com.tonapps.wallet.features.events.screens

import android.os.Bundle
import android.view.View
import com.tonapps.core.ComposableFragment
import com.tonapps.core.navigation.NavigationDelegate
import uikit.extensions.activity

class EventsFragment : ComposableFragment() {

    override val fragmentName: String = "EventsFragment"

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val delegate = context?.activity as? NavigationDelegate
        val assetId = arguments?.getString(ARG_ASSET_ID)

        setContent {
            EventsScreen(
                assetId = assetId,
                onBack = { finish() },
                onOpenDeposit = { delegate?.onOpenDeposit() },
                onOpenNft = { address -> delegate?.onOpenNft(address) },
            )
        }
    }

    companion object {
        private const val ARG_ASSET_ID = "asset_id"

        fun newInstance(assetId: String? = null) = EventsFragment().apply {
            if (assetId != null) {
                arguments = Bundle().apply {
                    putString(ARG_ASSET_ID, assetId)
                }
            }
        }
    }
}
