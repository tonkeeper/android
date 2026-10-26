package com.tonapps.tonkeeper.ui.screen.browser.dapp.earn

import com.tonapps.mvi.AsyncViewModel
import com.tonapps.tonkeeper.ui.screen.browser.dapp.inapp.InAppBridgeContract.Bridges

internal class EarnDAppViewModel(
    private val earnBridges: EarnDAppBridges,
) : AsyncViewModel(), Bridges by earnBridges {

    val analytics: EarnDAppAnalytics = earnBridges.earnAnalytics

    init {
        earnBridges.start(stateScope)
    }

    fun onThemeChanged() {
        earnBridges.onThemeChanged()
    }
}
