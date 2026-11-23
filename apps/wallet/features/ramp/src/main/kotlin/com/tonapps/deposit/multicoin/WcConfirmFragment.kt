package com.tonapps.deposit.multicoin

import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import com.tonapps.blockchain.model.ConfirmRequest
import com.tonapps.core.ComposableFragment
import com.tonapps.core.navigation.NavigationDelegate
import com.tonapps.deposit.multicoin.screens.confirm.ConfirmScreen
import com.tonapps.wallet.data.dapps.confirm.ConfirmSheetQueue
import com.tonapps.wallet.data.dapps.evm.EvmProviderRepository
import com.tonapps.wallet.data.dapps.wc.WcRepository
import com.tonapps.wallet.data.multichain.legacy.LegacySwapAccounts
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.koin.android.ext.android.inject
import uikit.base.BaseFragment
import uikit.extensions.activity
import uikit.navigation.Navigation.Companion.navigation

class WcConfirmFragment : ComposableFragment(), BaseFragment.BottomSheet, ConfirmSheetQueue.Sheet {

    override val fragmentName: String = "WcConfirmFragment"

    private val wcRepo: WcRepository by inject()

    private val evmProvider: EvmProviderRepository by inject()

    private val legacySwapAccounts: LegacySwapAccounts by inject()

    override val queuedRequestId: String?
        get() = arguments?.getString(ARG_REQUEST_ID)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val request = resolveRequest()
        if (request == null) {
            finish()
            return
        }

        val delegate = context?.activity as? NavigationDelegate

        setContent {
            ConfirmScreen(
                request = request,
                onClose = { finish() },
                onBack = null,
                onSendSuccess = { finish() },
                onOpenBattery = { walletId, from ->
                    delegate?.onOpenBattery(walletId = walletId, from = from)
                },
                onTopUp = { assetId -> topUp(request, assetId) },
                rejectOnClose = true,
            )
        }
    }

    private fun topUp(request: ConfirmRequest, assetId: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            navigation?.add(confirmTopUpScreen(request, assetId, legacySwapAccounts))
        }
    }

    private fun resolveRequest(): ConfirmRequest? {
        val args = requireArguments()
        args.getString(ARG_REQUEST)?.let { return Json.decodeFromString<ConfirmRequest>(it) }
        val requestId = args.getString(ARG_REQUEST_ID) ?: return null
        return wcRepo.pendingRequest(requestId) ?: evmProvider.pendingRequest(requestId)
    }

    companion object {
        private const val ARG_REQUEST_ID = "requestId"
        private const val ARG_REQUEST = "request"

        fun newInstance(requestId: String): BaseFragment {
            val fragment = WcConfirmFragment()
            fragment.putStringArg(ARG_REQUEST_ID, requestId)
            return fragment
        }

        fun newInstance(request: ConfirmRequest): BaseFragment {
            val fragment = WcConfirmFragment()
            fragment.putStringArg(ARG_REQUEST, Json.encodeToString(request))
            return fragment
        }
    }
}