package com.tonapps.wallet.data.tonconnect.entities

data class DAppEntity(
    val manifest: DAppManifestEntity,
    val connections: List<DConnectEntity>
)