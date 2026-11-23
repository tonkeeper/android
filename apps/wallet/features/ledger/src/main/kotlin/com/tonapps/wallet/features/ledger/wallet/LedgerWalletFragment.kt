package com.tonapps.wallet.features.ledger.wallet

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.tonapps.bus.generated.Events.LedgerFlow.LedgerDeviceViewFrom
import com.tonapps.core.ComposableFragment
import com.tonapps.wallet.features.ledger.common.displayName
import com.tonapps.wallet.features.ledger.common.ledgerFailureMessage
import com.tonapps.wallet.features.ledger.common.ledgerToast
import com.tonapps.wallet.features.ledger.pairing.LedgerPairingFragment
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import uikit.base.BaseFragment
import uikit.navigation.Navigation.Companion.navigation

class LedgerWalletFragment : ComposableFragment(), BaseFragment.BottomSheet {

    override val fragmentName: String = "LedgerWalletFragment"

    private val walletId: String
        get() = requireNotNull(requireArguments().getString(ARG_WALLET_ID))

    private val from: LedgerDeviceViewFrom
        get() = LedgerDeviceViewFrom.entries.firstOrNull { it.key == arguments?.getString(ARG_FROM) } ?: LedgerDeviceViewFrom.Settings

    private val feature: LedgerWalletFeature by viewModel { parametersOf(walletId, from) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setContent {
            LedgerWalletScreen(
                feature = feature,
                onClose = ::finish,
                onPairAgain = { navigation?.add(LedgerPairingFragment.newRepairInstance(walletId)) },
            )
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                feature.events.collect(::onEvent)
            }
        }
    }

    private fun onEvent(event: LedgerWalletEvent) {
        val context = context ?: return
        when (event) {
            LedgerWalletEvent.AddressVerified -> context.ledgerToast(getString(Localization.ledger_mc_address_verified))
            is LedgerWalletEvent.NetworksAdded -> context.ledgerToast(event.message(context))
            is LedgerWalletEvent.Failed -> context.ledgerToast(
                event.failure?.let { ledgerFailureMessage(context, it) } ?: getString(Localization.unknown_error),
            )
            is LedgerWalletEvent.ConnectionChecked -> context.ledgerToast(
                event.app?.let { getString(Localization.ledger_mc_connection_checked_app, it.displayName) }
                    ?: getString(Localization.ledger_mc_connection_checked),
            )
            LedgerWalletEvent.Close -> finish()
        }
    }

    private fun LedgerWalletEvent.NetworksAdded.message(context: Context): String = when {
        chains.isNotEmpty() && skipReason != null ->
            context.getString(Localization.ledger_mc_networks_added) + ". " + ledgerFailureMessage(context, skipReason)
        chains.isNotEmpty() -> context.getString(Localization.ledger_mc_networks_added)
        skipReason != null -> ledgerFailureMessage(context, skipReason)
        else -> context.getString(Localization.ledger_mc_nothing_to_add)
    }

    companion object {

        private const val ARG_WALLET_ID = "wallet_id"
        private const val ARG_FROM = "from"

        fun newInstance(walletId: String, from: LedgerDeviceViewFrom): LedgerWalletFragment {
            val fragment = LedgerWalletFragment()
            fragment.putStringArg(ARG_WALLET_ID, walletId)
            fragment.putStringArg(ARG_FROM, from.key)
            return fragment
        }
    }
}
