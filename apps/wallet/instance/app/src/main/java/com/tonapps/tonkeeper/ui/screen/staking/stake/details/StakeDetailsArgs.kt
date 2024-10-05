package com.tonapps.tonkeeper.ui.screen.staking.stake.details

import android.os.Bundle
import com.tonapps.blockchain.ton.extensions.equalsAddress
import com.tonapps.extensions.getParcelableCompat
import com.tonapps.icu.Coins
import com.tonapps.wallet.data.staking.entities.PoolEntity
import com.tonapps.wallet.data.staking.entities.PoolInfoEntity
import uikit.base.BaseArgs
import java.math.BigDecimal

data class StakeDetailsArgs(
    val info: PoolInfoEntity,
    val poolAddress: String,
): BaseArgs() {

    companion object {
        private const val ARG_INFO = "info"
        private const val ARG_POOL_ADDRESS = "pool_address"
    }

    val pool: PoolEntity by lazy {
        info.pools.firstOrNull { it.address.equalsAddress(poolAddress) } ?: info.pools.first()
    }

    val name: String
        get() = pool.name

    val maxApy: Boolean
        get() = pool.maxApy

    val apy: BigDecimal
        get() = pool.apy

    val minStake: Coins
        get() = pool.minStake

    val links: List<String> by lazy {
        info.details.getLinks(poolAddress)
    }

    constructor(bundle: Bundle) : this(
        info = bundle.getParcelableCompat(ARG_INFO)!!,
        poolAddress = bundle.getString(ARG_POOL_ADDRESS)!!
    )

    override fun toBundle(): Bundle = Bundle().apply {
        putParcelable(ARG_INFO, info)
        putString(ARG_POOL_ADDRESS, poolAddress)
    }
}