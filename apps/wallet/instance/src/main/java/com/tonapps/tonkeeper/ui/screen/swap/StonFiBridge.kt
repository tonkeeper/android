package com.tonapps.tonkeeper.ui.screen.swap

import android.webkit.JavascriptInterface

class StonFiBridge(private val parent: SwapScreen) {

    @JvmField

    val address: String = "test"

    @JvmOverloads
    @JavascriptInterface
    fun close() {
        parent.finish()
    }

    @JvmOverloads
    @JavascriptInterface
    fun sendTransaction(value: String) {

    }
}