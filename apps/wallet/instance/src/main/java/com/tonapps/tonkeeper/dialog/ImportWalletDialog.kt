package com.tonapps.tonkeeper.dialog

import android.content.Context
import android.view.View
import com.tonapps.tonkeeperx.R
import com.tonapps.tonkeeper.fragment.signer.add.SignerAddFragment
import com.tonapps.tonkeeper.koin.api
import com.tonapps.tonkeeper.ui.screen.init.InitArgs
import com.tonapps.tonkeeper.ui.screen.init.InitScreen
import com.tonapps.wallet.api.API
import uikit.base.BaseSheetDialog
import uikit.navigation.Navigation.Companion.navigation

class ImportWalletDialog(context: Context): BaseSheetDialog(context), View.OnClickListener {

    private companion object {
        private val associatedActions = mapOf(
            R.id.import_wallet to InitArgs.Type.Import,
            R.id.watch_wallet to InitArgs.Type.Watch,
            R.id.testnet_wallet to InitArgs.Type.Testnet,
            R.id.signer_wallet to InitArgs.Type.Signer,
        )
    }

    private val disableSigner = context.api?.config?.flags?.disableSigner ?: false

    init {
        setContentView(R.layout.dialog_import_wallet)

        findViewById<View>(R.id.import_wallet)!!.setOnClickListener(this)
        findViewById<View>(R.id.watch_wallet)!!.setOnClickListener(this)
        findViewById<View>(R.id.testnet_wallet)!!.setOnClickListener(this)

        val signerView = findViewById<View>(R.id.signer_wallet)!!
        signerView.setOnClickListener {
            navigation?.add(SignerAddFragment.newInstance())
            dismiss()
        }

        if (disableSigner) {
            signerView.visibility = View.GONE
        }
    }

    override fun onClick(v: View) {
        val nav = navigation ?: return
        val action = associatedActions[v.id] ?: return
        nav.add(InitScreen.newInstance(action))
        dismiss()
    }

}