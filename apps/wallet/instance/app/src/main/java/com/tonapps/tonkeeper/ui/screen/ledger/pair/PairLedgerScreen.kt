package com.tonapps.tonkeeper.ui.screen.ledger.pair

import android.os.Bundle
import android.view.View
import com.tonapps.tonkeeper.ui.screen.ledger.steps.LedgerConnectionFragment
import com.tonapps.tonkeeperx.R
import uikit.base.BaseFragment

class PairLedgerScreen : BaseFragment(R.layout.fragment_pair_ledger), BaseFragment.Modal {
    companion object {
        fun newInstance(
        ): PairLedgerScreen {
            return PairLedgerScreen()
        }
    }

    private val ledgerConnectionFragment: LedgerConnectionFragment by lazy {
        LedgerConnectionFragment.newInstance(false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


        view.findViewById<View>(R.id.close).setOnClickListener { reject() }
        view.findViewById<View>(R.id.cancel).setOnClickListener { reject() }
        view.findViewById<View>(R.id.continue_button).setOnClickListener { handleContinue() }

        if (savedInstanceState == null) {
            childFragmentManager.beginTransaction().replace(R.id.steps, ledgerConnectionFragment)
                .commit()
        }
    }

    private fun handleContinue() {
    }

    private fun reject() {
        finish()
    }
}