package com.tonapps.wallet.features.ledger.pairing

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.tonapps.bus.generated.Events.LedgerFlow.LedgerMigrationStartFrom
import com.tonapps.bus.generated.Events.WalletFlow.WalletFlowFrom
import com.tonapps.core.ComposableFragment
import com.tonapps.core.helper.navigationDelegate
import com.tonapps.wallet.features.ledger.common.ledgerFailureMessage
import com.tonapps.wallet.features.ledger.common.ledgerToast
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import uikit.base.BaseFragment

class LedgerPairingFragment : ComposableFragment(), BaseFragment.BottomSheet {

    override val fragmentName: String = "LedgerPairingFragment"

    private val mode: LedgerPairingMode
        get() {
            val repairId = arguments?.getString(ARG_REPAIR_WALLET_ID)
            val migrateId = arguments?.getString(ARG_MIGRATE_WALLET_ID)
            val from = arguments?.getString(ARG_FROM)
            return when {
                repairId != null -> LedgerPairingMode.Repair(repairId)
                migrateId != null -> LedgerPairingMode.Migrate(
                    walletId = migrateId,
                    from = LedgerMigrationStartFrom.entries.firstOrNull { it.key == from } ?: LedgerMigrationStartFrom.Settings,
                )
                else -> LedgerPairingMode.New(WalletFlowFrom.entries.firstOrNull { it.key == from } ?: WalletFlowFrom.Main)
            }
        }

    private val feature: LedgerPairingFeature by viewModel { parametersOf(mode) }

    override val predictiveBackPreviewEnabled: Boolean
        get() = feature.state.value.step == LedgerPairingStep.Connect

    private val environmentReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            feature.refreshReadiness()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setContent {
            LedgerPairingScreen(feature = feature, onClose = ::finish)
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                feature.events.collect(::onEvent)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(LocationManager.PROVIDERS_CHANGED_ACTION)
        }
        ContextCompat.registerReceiver(requireContext(), environmentReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        feature.onScreenVisible()
    }

    override fun onResume() {
        super.onResume()
        feature.refreshReadiness()
    }

    override fun onStop() {
        requireContext().unregisterReceiver(environmentReceiver)
        feature.onScreenHidden()
        super.onStop()
    }

    override fun onBackPressed(): Boolean {
        feature.back()
        return false
    }

    private fun onEvent(event: LedgerPairingEvent) {
        when (event) {
            is LedgerPairingEvent.Import -> {
                context?.navigationDelegate?.onOpenLedgerImport(event.payload, event.deviceName, (mode as? LedgerPairingMode.Migrate)?.from)
                finish()
            }
            LedgerPairingEvent.Repaired -> {
                context?.ledgerToast(getString(Localization.ledger_mc_paired))
                finish()
            }
            is LedgerPairingEvent.Failed -> context?.let { it.ledgerToast(ledgerFailureMessage(it, event.failure)) }
            LedgerPairingEvent.Close -> finish()
        }
    }

    companion object {

        private const val ARG_REPAIR_WALLET_ID = "repair_wallet_id"
        private const val ARG_MIGRATE_WALLET_ID = "migrate_wallet_id"
        private const val ARG_FROM = "from"

        fun newInstance(from: WalletFlowFrom): LedgerPairingFragment {
            val fragment = LedgerPairingFragment()
            fragment.putStringArg(ARG_FROM, from.key)
            return fragment
        }

        fun newRepairInstance(walletId: String): LedgerPairingFragment {
            val fragment = LedgerPairingFragment()
            fragment.putStringArg(ARG_REPAIR_WALLET_ID, walletId)
            return fragment
        }

        fun newMigrationInstance(walletId: String, from: LedgerMigrationStartFrom): LedgerPairingFragment {
            val fragment = LedgerPairingFragment()
            fragment.putStringArg(ARG_MIGRATE_WALLET_ID, walletId)
            fragment.putStringArg(ARG_FROM, from.key)
            return fragment
        }
    }
}
