package com.tonapps.tonkeeper.fragment.intro

import android.os.Bundle
import android.view.View
import android.widget.Button
import com.tonapps.tonkeeperx.R
import com.tonapps.tonkeeper.dialog.ImportWalletDialog
import com.tonapps.tonkeeper.ui.screen.init.InitAction
import com.tonapps.tonkeeper.ui.screen.init.InitFragment
import uikit.base.BaseFragment
import uikit.navigation.Navigation.Companion.navigation

class IntroFragment: BaseFragment(R.layout.fragment_intro) {

    companion object {
        fun newInstance() = IntroFragment()
    }

    private val importWalletDialog: ImportWalletDialog by lazy {
        ImportWalletDialog(requireContext())
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val newWalletButton = view.findViewById<Button>(R.id.new_wallet)
        newWalletButton.setOnClickListener {
            navigation?.add(InitFragment.newInstance(InitAction.Create))
        }

        val importWalletButton = view.findViewById<Button>(R.id.import_wallet)
        importWalletButton.setOnClickListener {
            importWalletDialog.show()
        }
    }
}