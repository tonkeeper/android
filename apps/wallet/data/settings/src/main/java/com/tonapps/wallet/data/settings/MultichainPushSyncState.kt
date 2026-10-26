package com.tonapps.wallet.data.settings

data class MultichainPushSyncState(
    val token: String,
    val deviceId: String?,
    val locale: String,
    val walletIds: Set<String>,
)
