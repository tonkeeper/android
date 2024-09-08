package com.tonapps.tonkeeper.ui.base

import android.content.Context
import android.os.Bundle
import uikit.navigation.NavigationActivity

abstract class BaseWalletActivity: NavigationActivity(), BaseWalletVM.Holder {

    abstract val viewModel: BaseWalletVM

    override val uiContext: Context
        get() = this

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.attachHolder(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.detachHolder()
    }
}