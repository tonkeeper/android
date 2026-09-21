package com.tonapps.dapp.screens.session

import android.os.Bundle
import android.view.View
import com.tonapps.core.ComposableFragment
import com.tonapps.wallet.data.dapps.wc.WcRepository
import org.koin.android.ext.android.inject
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import uikit.base.BaseFragment
import uikit.navigation.Navigation.Companion.navigation

class WcSessionFragment : ComposableFragment(), BaseFragment.BottomSheet {

    override val fragmentName: String = "WcSessionFragment"

    private val wcRepo: WcRepository by inject()

    private val requestId: String
        get() = requireArguments().getString(ARG_REQUEST_ID)!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val proposal = wcRepo.pendingProposal(requestId)
        if (proposal == null) {
            finish()
            return
        }

        setContent {
            val viewModel = koinViewModel<WcSessionFeature> { parametersOf(proposal) }
            WcSessionScreen(
                feature = viewModel,
                onBack = { finish() },
                onOpenNetworks = { accounts ->
                    navigation?.add(WcNetworksFragment.newInstance(accounts))
                },
            )
        }
    }

    companion object {
        private const val ARG_REQUEST_ID = "requestId"

        fun newInstance(requestId: String): BaseFragment {
            val fragment = WcSessionFragment()
            fragment.putStringArg(ARG_REQUEST_ID, requestId)
            return fragment
        }
    }
}
