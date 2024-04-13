package com.tonapps.tonkeeper.ui.screen.start

import android.os.Bundle
import android.view.View
import android.widget.Button
import com.tonapps.tonkeeper.dialog.ImportWalletDialog
import com.tonapps.tonkeeper.ui.screen.init.InitArgs
import com.tonapps.tonkeeper.ui.screen.init.InitScreen
import com.tonapps.tonkeeperx.R
import uikit.base.BaseFragment
import uikit.extensions.applyNavBottomPadding
import uikit.navigation.Navigation.Companion.navigation

class StartScreen: BaseFragment(R.layout.fragment_intro) {

    private val importWalletDialog: ImportWalletDialog by lazy {
        ImportWalletDialog(requireContext())
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.applyNavBottomPadding()

        val newWalletButton = view.findViewById<Button>(R.id.new_wallet)
        newWalletButton.setOnClickListener {
            navigation?.add(InitScreen.newInstance(InitArgs.Type.New))
        }

        val importWalletButton = view.findViewById<Button>(R.id.import_wallet)
        importWalletButton.setOnClickListener {
            importWalletDialog.show()
        }
    }

    companion object {
        fun newInstance() = StartScreen()
    }
}