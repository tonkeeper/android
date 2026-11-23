package com.tonapps.tonkeeper.ui.screen.root

import android.net.Uri
import androidx.lifecycle.SavedStateHandle

class RootModelState(private val savedStateHandle: SavedStateHandle) {

    var returnUri: Uri?
        get() = savedStateHandle[RETURN_URI_KEY]
        set(value) = savedStateHandle.set(RETURN_URI_KEY, value)

    var walletAwaitingLink: WalletAwaitingLink?
        get() = savedStateHandle[WALLET_AWAITING_LINK_KEY]
        set(value) = savedStateHandle.set(WALLET_AWAITING_LINK_KEY, value)

    private companion object {
        private const val RETURN_URI_KEY = "return_uri"
        private const val WALLET_AWAITING_LINK_KEY = "wallet_awaiting_link"
    }
}
