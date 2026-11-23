package com.tonapps.wallet.data.multichain.lighter

data class IntegratorApprovalKey(
    val accountIndex: Long,
    val integratorAccountIndex: Long,
    val maxPerpsTakerFee: Int,
    val maxPerpsMakerFee: Int,
    val maxSpotTakerFee: Int,
    val maxSpotMakerFee: Int,
)

data class StoredIntegratorApproval(
    val key: IntegratorApprovalKey,
    val expiryMs: Long,
)
