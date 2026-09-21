package com.tonapps.wc.models

data class WcWalletConfig(
    val projectId: String,
    val appName: String,
    val appDescription: String,
    val appUrl: String,
    val icon: String,
    val relayUrl: String?,
    val redirect: String?,
)
