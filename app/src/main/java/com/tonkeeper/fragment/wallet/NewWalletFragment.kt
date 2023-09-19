package com.tonkeeper.fragment.wallet

import android.os.Bundle
import android.view.View
import android.widget.Button
import com.tonkeeper.R
import com.tonkeeper.fragment.ImportWalletFragment
import com.tonkeeper.fragment.Navigation.Companion.nav
import com.tonkeeper.fragment.PasscodeFragment
import com.tonkeeper.uikit.base.BaseFragment

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
            nav()?.replace(PasscodeFragment.newInstance())
        }

        buttonImportWallet = view.findViewById(R.id.import_wallet)
        buttonImportWallet.setOnClickListener {
            nav()?.replace(ImportWalletFragment.newInstance())
        }
    }
}