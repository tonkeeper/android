package com.tonapps.tonkeeper.ui.screen.root

import android.net.Uri
import androidx.lifecycle.SavedStateHandle

class RootModelState(private val savedStateHandle: SavedStateHandle) {

    private companion object {
        private const val RETURN_URI_KEY = "return_uri"
    }

    var returnUri: Uri?
        get() = savedStateHandle[RETURN_URI_KEY]
        set(value) = savedStateHandle.set(RETURN_URI_KEY, value)
}