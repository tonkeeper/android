package com.tonkeeper.fragment.intro

import android.content.Context
import android.widget.Button
import com.tonkeeper.R
import com.tonkeeper.fragment.Navigation.Companion.nav
import com.tonkeeper.fragment.wallet.creating.CreatingWalletFragment
import com.tonkeeper.uikit.base.BaseSheetDialog

internal class IntroWalletDialog(context: Context): BaseSheetDialog(context) {
    init {
        setContentView(R.layout.dialog_start)

        val newWalletButton = findViewById<Button>(R.id.new_wallet)!!
        newWalletButton.setOnClickListener {
            newWallet()
        }
    }

    private fun newWallet() {
        dismiss()
        nav()?.replace(CreatingWalletFragment.newInstance())
    }
}