package com.tonapps.wallet.data.staking

import com.tonapps.blockchain.ton.TONOpCode
import com.tonapps.blockchain.ton.extensions.storeOpCode
import org.ton.block.AddrStd
import org.ton.block.Coins
import org.ton.cell.Cell
import org.ton.cell.buildCell
import org.ton.tlb.storeTlb
import java.math.BigInteger

object StakingUtils {

    fun getWithdrawalFee(implementation: StakingPool.Implementation): Double {
        if (implementation == StakingPool.Implementation.Whales) {
            return 0.2
        }
        if (implementation == StakingPool.Implementation.TF || implementation == StakingPool.Implementation.LiquidTF) {
            return 1.0
        }
        return 0.0
    }

    fun getWithdrawalAlertFee(
        implementation: StakingPool.Implementation,
        forDisplay: Boolean = false
    ): Double {
        if (implementation == StakingPool.Implementation.Whales) {
            return 0.4
        }
        if (implementation == StakingPool.Implementation.TF) {
            return 1.0
        }
        if (implementation == StakingPool.Implementation.LiquidTF) {
            return if (forDisplay) 2.0 else 1.0
        }
        return 0.0
    }

    fun createWhalesAddStakeCommand(
        queryId: BigInteger
    ): Cell {
        return buildCell {
            storeOpCode(TONOpCode.WHALES_DEPOSIT)
            storeUInt(queryId, 64)
            storeTlb(Coins, Coins.ofNano(100000))
        }
    }

    fun createWhalesWithdrawStakeCell(
        queryId: BigInteger,
        amount: Coins
    ): Cell {
        return buildCell {
            storeUInt(3665837821, 32)
            storeUInt(queryId, 64)
            storeTlb(Coins, Coins.ofNano(100000))
            storeTlb(Coins, amount)
        }
    }

    fun createLiquidTfAddStakeCommand(queryId: BigInteger): Cell {
        return buildCell {
            storeUInt(0x47d54391, 32)
            storeUInt(queryId, 64)
            storeUInt(0x000000000005b7ce, 64)
        }
    }

    fun createLiquidTfWithdrawStakeCell(
        queryId: BigInteger,
        amount: Coins,
        address: AddrStd
    ): Cell {
        val customPayload = buildCell {
            storeUInt(1, 1)
            storeUInt(0, 1)
        }

        return buildCell {
            storeOpCode(TONOpCode.LIQUID_TF_BURN)
            storeUInt(queryId, 64)
            storeTlb(Coins, amount)
            storeTlb(AddrStd, address)
            storeBit(true)
            refs.add(customPayload)
        }
    }

    fun createTfAddStakeCommand(): Cell {
        return buildCell {
            storeUInt(0, 32)
            storeBytes("d".toByteArray())
        }
    }

    fun createTfWithdrawStakeCell(): Cell {
        return buildCell {
            storeUInt(0, 32)
            storeBytes("w".toByteArray())
        }
    }
}
