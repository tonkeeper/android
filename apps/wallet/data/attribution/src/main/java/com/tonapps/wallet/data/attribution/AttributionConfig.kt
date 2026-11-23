package com.tonapps.wallet.data.attribution

data class AttributionConfig(
    val appsFlyerDevKey: String,
    val isDebug: Boolean,
    val installReferrer: suspend () -> String?,
)
