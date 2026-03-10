package com.tonapps.tonkeeper.ui.screen.start

import android.os.Bundle
import android.view.View
import android.widget.Button
import com.tonapps.tonkeeper.ui.screen.add.AddWalletScreen
import com.tonapps.tonkeeper.ui.screen.dev.DevScreen
import com.tonapps.tonkeeper.ui.screen.init.InitArgs
import com.tonapps.tonkeeper.ui.screen.init.InitScreen
import com.tonapps.tonkeeperx.R
import uikit.base.BaseFragment
import uikit.extensions.applyNavBottomPadding
import uikit.navigation.Navigation.Companion.navigation

class StartScreen: BaseFragment(R.layout.fragment_intro) {

    override val fragmentName: String = "StartScreen"

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.applyNavBottomPadding()
        
        view.findViewById<View>(R.id.logo).setOnLongClickListener {
            navigation?.add(DevScreen.newInstance())
            true
        }

        val newWalletButton = view.findViewById<Button>(R.id.new_wallet)
        newWalletButton.setOnClickListener {
            navigation?.add(InitScreen.newInstance(InitArgs.Type.New))
        }

        val importWalletButton = view.findViewById<Button>(R.id.import_wallet)
        importWalletButton.setOnClickListener {
            navigation?.add(AddWalletScreen.newInstance(false))
        }
    }

    companion object {
        fun newInstance() = StartScreen()
    }
}