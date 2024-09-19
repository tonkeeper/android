package com.tonapps.tonkeeper.ui.screen.add.main

import android.os.Bundle
import android.view.View
import androidx.core.widget.NestedScrollView
import com.tonapps.tonkeeper.ui.screen.external.qr.keystone.add.KeystoneAddScreen
import com.tonapps.tonkeeper.ui.screen.external.qr.signer.add.SignerAddScreen
import com.tonapps.tonkeeper.ui.screen.init.InitArgs
import com.tonapps.tonkeeper.ui.screen.init.InitScreen
import com.tonapps.tonkeeper.ui.screen.ledger.pair.PairLedgerScreen
import com.tonapps.tonkeeperx.R
import com.tonapps.wallet.api.API
import org.koin.android.ext.android.inject
import uikit.base.BaseFragment
import uikit.extensions.collectFlow
import uikit.extensions.pinToBottomInsets
import uikit.extensions.topScrolled
import uikit.navigation.Navigation.Companion.navigation
import uikit.widget.ModalHeader

class AddScreen: BaseFragment(R.layout.fragment_add_wallet), BaseFragment.Modal {

    private val api: API by inject()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val headerView = view.findViewById<ModalHeader>(R.id.header)
        headerView.onCloseClick = { finish() }

        val scrollView = view.findViewById<NestedScrollView>(R.id.scroll)
        scrollView.pinToBottomInsets()
        collectFlow(scrollView.topScrolled, headerView::setDivider)

        openByClick(R.id.new_wallet, InitArgs.Type.New)
        openByClick(R.id.import_wallet, InitArgs.Type.Import)
        openByClick(R.id.watch_wallet, InitArgs.Type.Watch)
        openByClick(R.id.testnet_wallet, InitArgs.Type.Testnet)

        val signerView = view.findViewById<View>(R.id.signer_wallet)
        signerView.setOnClickListener {
            navigation?.add(SignerAddScreen.newInstance())
        }

        if (api.config.flags.disableSigner) {
            signerView.visibility = View.GONE
        }

        view.findViewById<View>(R.id.keystone_wallet).setOnClickListener {
            navigation?.add(KeystoneAddScreen.newInstance())
        }

        val ledgerView = view.findViewById<View>(R.id.ledger_wallet)
        ledgerView.setOnClickListener {
            navigation?.add(PairLedgerScreen.newInstance())
        }
    }

    private fun openByClick(id: Int, type: InitArgs.Type) {
        view?.findViewById<View>(id)?.setOnClickListener {
            navigation?.add(InitScreen.newInstance(type))
        }
    }

    companion object {
        fun newInstance() = AddScreen()
    }
}