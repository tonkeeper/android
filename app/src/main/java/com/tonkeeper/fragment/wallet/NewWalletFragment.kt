package com.tonkeeper.fragment.wallet

import android.os.Bundle
import android.view.View
import android.widget.Button
import com.tonkeeper.R
import com.tonkeeper.fragment.wallet.restore.RestoreWalletScreen
import uikit.navigation.Navigation.Companion.nav
import com.tonkeeper.fragment.passcode.PasscodeScreen
import uikit.base.fragment.BaseFragment

class NewWalletFragment: BaseFragment(R.layout.fragment_new_wallet) {

    companion object {
        fun newInstance() = NewWalletFragment()
    }

    private lateinit var buttonCreateNewWallet: Button
    private lateinit var buttonImportWallet: Button

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        buttonCreateNewWallet = view.findViewById(R.id.create_new_wallet)
        buttonCreateNewWallet.setOnClickListener {
            nav()?.replace(PasscodeScreen.newInstance(PasscodeScreen.STATE_CREATE), true)
        }

        buttonImportWallet = view.findViewById(R.id.import_wallet)
        buttonImportWallet.setOnClickListener {
            nav()?.replace(RestoreWalletScreen.newInstance(), true)
        }
    }
}