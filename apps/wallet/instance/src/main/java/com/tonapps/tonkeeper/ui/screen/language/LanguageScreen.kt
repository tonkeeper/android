package com.tonapps.tonkeeper.ui.screen.language

import uikit.base.BaseFragment
import uikit.base.BaseListFragment

class LanguageScreen: BaseListFragment(), BaseFragment.SwipeBack {

    companion object {
        fun newInstance() = LanguageScreen()
    }
}