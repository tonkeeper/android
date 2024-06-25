package com.tonapps.tonkeeper.ui.screen.ledger.pair

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import androidx.fragment.app.viewModels
import com.tonapps.ledger.ton.LedgerAccount
import com.tonapps.ledger.ton.AccountPath
import com.tonapps.ledger.ton.LedgerConnectData
import com.tonapps.tonkeeper.ui.screen.ledger.steps.LedgerConnectionFragment
import com.tonapps.tonkeeper.ui.screen.ledger.steps.LedgerConnectionViewModel
import com.tonapps.tonkeeper.ui.screen.root.RootViewModel
import com.tonapps.tonkeeperx.R
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import uikit.base.BaseFragment
import uikit.extensions.collectFlow

class PairLedgerScreen : BaseFragment(R.layout.fragment_pair_ledger), BaseFragment.Modal {
    companion object {
        fun newInstance(
        ): PairLedgerScreen {
            return PairLedgerScreen()
        }
    }

    private val rootViewModel: RootViewModel by activityViewModel()

    private val connectionViewModel: LedgerConnectionViewModel by viewModels()

    private val ledgerConnectionFragment: LedgerConnectionFragment by lazy {
        LedgerConnectionFragment.newInstance(false)
    }

    private lateinit var continueButton: Button

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        continueButton = view.findViewById(R.id.continue_button)

        view.findViewById<View>(R.id.close).setOnClickListener { reject() }
        view.findViewById<View>(R.id.cancel).setOnClickListener { reject() }
        continueButton.setOnClickListener { handleContinue() }

        if (savedInstanceState == null) {
            childFragmentManager.beginTransaction().replace(R.id.steps, ledgerConnectionFragment)
                .commit()
        }

        collectFlow(connectionViewModel.tonTransport) { tonTransport ->
            continueButton.isEnabled = tonTransport != null
        }
    }

    private fun handleContinue() {
        collectFlow(connectionViewModel.connectionData) {
            val connectedDevice = it.first ?: return@collectFlow
            val tonTransport = it.second ?: return@collectFlow
            val accounts = mutableListOf<LedgerAccount>()
            for (i in 0 until 10) {
                val account = tonTransport.getAccount(AccountPath(i))
                accounts.add(account)
            }
            Log.d("PairLedgerScreen", "Accounts: $accounts")
            Log.d("PairLedgerScreen", "connectedDevice: $connectedDevice")

            rootViewModel.connectLedger(LedgerConnectData(accounts, connectedDevice.deviceId, connectedDevice.model))
            finish()
        }
    }

    private fun reject() {
        finish()
    }
}