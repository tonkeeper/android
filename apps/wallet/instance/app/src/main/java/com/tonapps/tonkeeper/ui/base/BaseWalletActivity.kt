package com.tonapps.tonkeeper.ui.base

import android.os.Bundle
import uikit.navigation.NavigationActivity

abstract class BaseWalletActivity: NavigationActivity() {

    abstract val viewModel: BaseWalletVM

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.attachActivity(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.detachActivity()
    }
}