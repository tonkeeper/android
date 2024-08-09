package com.tonapps.tonkeeper.ui.screen.ledger.update

import android.os.Bundle
import android.view.View
import android.widget.TextView
import com.tonapps.tonkeeperx.R
import com.tonapps.wallet.localization.Localization
import uikit.base.BaseFragment

class LedgerUpdateScreen : BaseFragment(R.layout.fragment_ledger_update_screen), BaseFragment.Modal {

    private val args: LedgerUpdateArgs by lazy { LedgerUpdateArgs(requireArguments()) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


        view.findViewById<View>(R.id.close).setOnClickListener { finish() }
        view.findViewById<View>(R.id.button_ok).setOnClickListener { finish() }

        view.findViewById<TextView>(R.id.update_title).text = getString(Localization.ledger_update_title, args.requiredVersion)
    }

    companion object {
        fun newInstance(requiredVersion: String): LedgerUpdateScreen {
            return LedgerUpdateScreen().apply {
                setArgs(LedgerUpdateArgs(requiredVersion))
            }
        }
    }
}