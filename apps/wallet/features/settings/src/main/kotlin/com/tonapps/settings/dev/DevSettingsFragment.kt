package com.tonapps.settings.dev

import android.os.Bundle
import android.view.View
import com.tonapps.core.ComposableFragment
import uikit.base.BaseFragment

class DevSettingsFragment : ComposableFragment(), BaseFragment.BottomSheet {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val startRoute = requireArguments().getString(ARG_START_ROUTE)

        setContent {
            DevSettingsRouter(
                startRoute = startRoute,
                onClose = { finish() },
            )
        }
    }

    companion object {
        private const val ARG_START_ROUTE = "start_route"

        fun newInstance(startRoute: String): DevSettingsFragment {
            val fragment = DevSettingsFragment()
            fragment.putStringArg(ARG_START_ROUTE, startRoute)
            return fragment
        }
    }
}
