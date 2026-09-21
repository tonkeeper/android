package com.tonapps.dapp.screens.sessions

import android.os.Bundle
import android.view.View
import com.tonapps.core.ComposableFragment
import com.tonapps.core.helper.navigationDelegate
import org.koin.androidx.compose.koinViewModel
import uikit.base.BaseFragment
import uikit.navigation.Navigation.Companion.navigation

class WcSessionsFragment : ComposableFragment(), BaseFragment.BottomSheet {

    override val fragmentName: String = "WcSessionsFragment"

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setContent {
            val viewModel = koinViewModel<WcSessionsFeature>()
            WcSessionsScreen(
                feature = viewModel,
                onBack = { finish() },
                onExploreApps = {
                    val delegate = context?.navigationDelegate
                    navigation?.popToRoot()
                    delegate?.onProcessDeeplink("tonkeeper://browser")
                },
            )
        }
    }

    companion object {
        fun newInstance(): BaseFragment = WcSessionsFragment()
    }
}
