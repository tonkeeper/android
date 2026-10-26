package com.tonapps.wallet.data.staking

import com.tonapps.apps.wallet.data.staking.R
import com.tonapps.icu.Coins
import io.tonapi.models.PoolImplementationType

private val GAS_RESERVE = Coins.of(0.1)

object StakingPool {

    enum class Implementation(
        val title: String
    ) {
        Whales("whales"), TF("tf"), LiquidTF("liquidTF")
    }

    fun implementation(type: PoolImplementationType): Implementation? {
        return when (type) {
            PoolImplementationType.whales -> Implementation.Whales
            PoolImplementationType.tf -> Implementation.TF
            PoolImplementationType.liquidTF -> Implementation.LiquidTF
            else -> null
        }
    }

    fun getTitle(implementation: Implementation): Int {
        return when (implementation) {
            Implementation.Whales -> R.string.stake_whales
            Implementation.TF -> R.string.stake_nominators
            Implementation.LiquidTF -> R.string.stake_tonstakers
        }
    }

    fun getIcon(implementation: Implementation): Int {
        return when (implementation) {
            Implementation.Whales -> R.drawable.staking_whales
            Implementation.TF -> R.drawable.staking_ton_nominators
            Implementation.LiquidTF -> R.drawable.staking_tonstakers
        }
    }

    fun getTotalFee(fee: Coins, implementation: Implementation): Coins {
        return fee.abs() + getFeeSurcharge(implementation)
    }

    fun isReserveIncludedInFee(implementation: Implementation): Boolean {
        return !getFeeSurcharge(implementation).isZero
    }

    private fun getFeeSurcharge(implementation: Implementation): Coins {
        return when (implementation) {
            Implementation.Whales -> Coins.of(0.2) // TODO fees: hardcoded formated
            Implementation.TF -> Coins.ONE // TODO fees: hardcoded formated
            Implementation.LiquidTF -> Coins.ZERO
        }
    }

    fun getStakeTemporaryReserve(implementation: Implementation): Coins {
        return when (implementation) {
            Implementation.LiquidTF -> Coins.ONE
            Implementation.Whales, Implementation.TF -> Coins.ZERO
        }
    }

    fun hasStakeTemporaryReserve(implementation: Implementation): Boolean {
        return !getStakeTemporaryReserve(implementation).isZero
    }

    fun getUnstakeTemporaryReserve(implementation: Implementation): Coins {
        return when (implementation) {
            Implementation.LiquidTF, Implementation.TF -> Coins.ONE
            Implementation.Whales -> Coins.of(0.2)
        }
    }

    fun getStakeMinRemaining(implementation: Implementation): Coins {
        return getStakeTemporaryReserve(implementation) + GAS_RESERVE
    }

}