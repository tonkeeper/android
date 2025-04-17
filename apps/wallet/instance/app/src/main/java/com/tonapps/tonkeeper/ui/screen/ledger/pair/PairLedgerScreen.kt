package com.tonapps.tonkeeper.ui.screen.ledger.pair

import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import com.tonapps.tonkeeper.extensions.toast
import com.tonapps.tonkeeper.ui.screen.ledger.steps.LedgerConnectionFragment
import com.tonapps.tonkeeper.ui.screen.ledger.steps.LedgerConnectionType
import com.tonapps.tonkeeper.ui.screen.ledger.steps.LedgerConnectionViewModel
import com.tonapps.tonkeeper.ui.screen.ledger.steps.LedgerEvent
import com.tonapps.tonkeeper.ui.screen.root.RootViewModel
import com.tonapps.tonkeeperx.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel
import uikit.base.BaseFragment
import uikit.extensions.applyNavBottomPadding
import uikit.extensions.collectFlow
import uikit.extensions.getDimensionPixelSize
import uikit.navigation.Navigation.Companion.navigation
import uikit.widget.LoadableButton

class PairLedgerScreen : BaseFragment(R.layout.fragment_ledger_pair), BaseFragment.Modal {

    override val fragmentName: String = "PairLedgerScreen"

    private val rootViewModel: RootViewModel by activityViewModel()

    private val connectionViewModel: LedgerConnectionViewModel by viewModel()

    private val ledgerConnectionFragment: LedgerConnectionFragment by lazy {
        LedgerConnectionFragment.newInstance()
    }

    private lateinit var continueButton: LoadableButton
    private lateinit var tabUsbView: View
    private lateinit var tabBluetoothView: View

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<View>(R.id.container)
            .applyNavBottomPadding(requireContext().getDimensionPixelSize(uikit.R.dimen.offsetMedium))

        continueButton = view.findViewById(R.id.continue_button)

        tabUsbView = view.findViewById(R.id.tab_usb)
        tabUsbView.setOnClickListener { connectionViewModel.setConnectionType(LedgerConnectionType.USB) }

        tabBluetoothView = view.findViewById(R.id.tab_bluetooth)
        tabBluetoothView.setOnClickListener { connectionViewModel.setConnectionType(LedgerConnectionType.BLUETOOTH) }

        view.findViewById<View>(R.id.close).setOnClickListener { finish() }
        view.findViewById<View>(R.id.cancel).setOnClickListener { finish() }
        continueButton.isEnabled = false
        continueButton.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) { connectionViewModel.getConnectData() }
        }

        if (savedInstanceState == null) {
            childFragmentManager.beginTransaction().replace(R.id.steps, ledgerConnectionFragment)
                .commit()
        }

        collectFlow(connectionViewModel.eventFlow, ::onEvent)
        collectFlow(connectionViewModel.connectionType, ::onConnectionType)
    }

    private fun onConnectionType(type: LedgerConnectionType) {
        when (type) {
            LedgerConnectionType.USB -> {
                tabUsbView.setBackgroundResource(uikit.R.drawable.bg_button_tertiary)
                tabBluetoothView.background = null
            }
            LedgerConnectionType.BLUETOOTH -> {
                tabUsbView.background = null
                tabBluetoothView.setBackgroundResource(uikit.R.drawable.bg_button_tertiary)
            }
        }
    }

    private fun onEvent(event: LedgerEvent) {
        when (event) {
            is LedgerEvent.Ready -> {
                continueButton.isEnabled = event.isReady
            }

            is LedgerEvent.Loading -> {
                continueButton.isLoading = event.loading
            }

            is LedgerEvent.Error -> {
                navigation?.toast(event.message)
            }

            is LedgerEvent.Next -> {
                rootViewModel.connectLedger(event.connectData, event.accounts)
                finish()
            }

            else -> {}
        }
    }

    companion object {

        fun newInstance(): PairLedgerScreen {
            return PairLedgerScreen()
        }
    }
}