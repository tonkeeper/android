package com.tonapps.wallet.data.collectibles.entities

data class NftListResult(
    val cache: Boolean = false,
    val list: List<NftEntity>? = null
)