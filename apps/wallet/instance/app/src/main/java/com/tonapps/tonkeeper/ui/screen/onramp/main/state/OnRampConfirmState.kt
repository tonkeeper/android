package com.tonapps.tonkeeper.ui.screen.onramp.main.state

data class OnRampConfirmState(
    val fromFormat: CharSequence = "",
    val toFormat: CharSequence = "",
    val loading: Boolean = false,
    val unavailable: Boolean = false,
    val webViewLink: String? = null
)