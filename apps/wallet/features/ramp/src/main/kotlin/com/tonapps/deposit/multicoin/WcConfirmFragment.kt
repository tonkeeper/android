package com.tonapps.deposit.multicoin

import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import com.tonapps.blockchain.model.ConfirmContext
import com.tonapps.blockchain.model.ConfirmRequest
import com.tonapps.bus.generated.Events.DepositFlow.DepositFlowFrom
import com.tonapps.core.ComposableFragment
import com.tonapps.core.navigation.NavigationDelegate
import com.tonapps.deposit.multicoin.analytics.asDepositFlowFrom
import com.tonapps.deposit.multicoin.screens.confirm.ConfirmScreen
import com.tonapps.deposit.screens.qr.QrAssetFragment
import com.tonapps.log.L
import com.tonapps.deposit.screens.ramp.RampType
import com.tonapps.wallet.data.dapps.confirm.ConfirmSheetQueue
import com.tonapps.wallet.data.dapps.evm.EvmProviderRepository
import com.tonapps.wallet.data.dapps.wc.WcRepository
import com.tonapps.wallet.data.multichain.legacy.LegacySwapAccounts
import com.tonapps.wallet.data.multichain.legacy.LegacySwapAssets
import kotlinx.coroutines.CancellationException
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
            // finish() hides the sheet, and a sheet that has not been shown yet is already hidden:
            // nothing would remove it, and it would open empty. Posted, since a restored fragment
            // gets here in the middle of a fragment transaction.
            view.post { finishInternal() }
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

    // The multichain ramp only knows wallets the backend registered: a legacy wallet swapping
    // GRAM ↔ USDT TRC20 tops up through its own receive screen, as its sends do.
    private fun topUp(request: ConfirmRequest, assetId: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            // A lookup that fails leaves the button working as it did before legacy swaps.
            val legacyWallet = try {
                legacySwapAccounts.find(request.data.walletId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                L.e(e)
                null
            }
            val legacyToken = LegacySwapAssets.token(assetId)
            val screen = if (legacyWallet != null && legacyToken != null) {
                QrAssetFragment.newInstance(
                    token = legacyToken,
                    walletId = legacyWallet.id,
                    analyticsFrom = request.topUpFrom(),
                )
            } else {
                DepositMulticoinFragment.create(
                    rampType = RampType.RampOn,
                    initial = DepositMulticoinRoutes.Buy(assetId),
                    analyticsFrom = request.topUpFrom(),
                )
            }
            navigation?.add(screen)
        }
    }

    private fun ConfirmRequest.topUpFrom(): DepositFlowFrom {
        if (context is ConfirmContext.Dapp) {
            return DepositFlowFrom.Walletconnect
        }
        return initiator.asDepositFlowFrom()
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