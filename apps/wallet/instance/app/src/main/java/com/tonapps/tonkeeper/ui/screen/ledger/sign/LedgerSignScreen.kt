package com.tonapps.tonkeeper.ui.screen.ledger.sign

import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import com.tonapps.blockchain.ton.extensions.toByteArray
import com.tonapps.ledger.ton.Transaction
import com.tonapps.tonkeeper.extensions.toast
import com.tonapps.tonkeeper.ui.screen.ledger.steps.LedgerConnectionFragment
import com.tonapps.tonkeeper.ui.screen.ledger.steps.LedgerConnectionViewModel
import com.tonapps.tonkeeper.ui.screen.ledger.steps.LedgerEvent
import com.tonapps.tonkeeperx.R
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.ton.cell.Cell
import uikit.base.BaseFragment
import uikit.extensions.collectFlow
import uikit.navigation.Navigation.Companion.navigation

class LedgerSignScreen : BaseFragment(R.layout.fragment_ledger_sign), BaseFragment.Modal {

    private val args: LedgerSignArgs by lazy { LedgerSignArgs(requireArguments()) }

    private val connectionViewModel: LedgerConnectionViewModel by viewModel()

    private val ledgerConnectionFragment: LedgerConnectionFragment by lazy {
        LedgerConnectionFragment.newInstance()
    }

    private var isSuccessful: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        connectionViewModel.setSignData(args.transaction, args.walletId)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


        view.findViewById<View>(R.id.close).setOnClickListener { finish() }
        view.findViewById<View>(R.id.cancel).setOnClickListener { finish() }

        if (savedInstanceState == null) {
            childFragmentManager.beginTransaction().replace(R.id.steps, ledgerConnectionFragment)
                .commit()
        }

        collectFlow(connectionViewModel.eventFlow, ::onEvent)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!requireActivity().isChangingConfigurations && !isSuccessful) {
            navigation?.setFragmentResult(args.requestKey, Bundle())
        }
    }

    private fun onEvent(event: LedgerEvent) {
        when (event) {
            is LedgerEvent.Ready -> {
                lifecycleScope.launch { connectionViewModel.signTransaction() }
            }
            is LedgerEvent.Error -> {
                navigation?.toast(event.message)
                finish()
            }
            is LedgerEvent.SignedTransaction -> {
                onSuccess(event.body)
            }
            is LedgerEvent.Rejected -> {
                finish()
            }
            else -> null
        }
    }

    private fun onSuccess(body: Cell) {
        navigation?.setFragmentResult(args.requestKey, Bundle().apply {
            putByteArray(SIGNED_MESSAGE, body.toByteArray())
        })
        isSuccessful = true
        finish()
    }

    companion object {
        const val SIGNED_MESSAGE = "signed_message"

        fun newInstance(transaction: Transaction, walletId: String, requestKey: String): LedgerSignScreen {
            return LedgerSignScreen().apply {
                setArgs(LedgerSignArgs(transaction, walletId, requestKey))
            }
        }
    }
}