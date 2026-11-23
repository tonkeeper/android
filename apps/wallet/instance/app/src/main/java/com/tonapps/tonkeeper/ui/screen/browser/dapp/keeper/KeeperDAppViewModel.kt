package com.tonapps.tonkeeper.ui.screen.browser.dapp.keeper

import com.tonapps.mvi.AsyncViewModel
import com.tonapps.webview.bridge.InAppBridgeContract.Bridges

internal class KeeperDAppViewModel(
    keeperBridges: KeeperDAppBridges,
) : AsyncViewModel(), Bridges by keeperBridges {

    init {
        keeperBridges.start(stateScope)
    }
}
