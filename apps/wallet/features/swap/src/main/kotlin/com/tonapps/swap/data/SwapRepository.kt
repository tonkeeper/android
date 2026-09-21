package com.tonapps.swap.data

import com.tonapps.wallet.data.multichain.asset.AssetEntity

interface SwapRepository {
    fun findPair(from: AssetEntity? = null)
    fun quote() {}
}

class SwapRepositoryDefault