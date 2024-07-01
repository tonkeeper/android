package com.tonapps.wallet.data.staking

import com.tonapps.blockchain.ton.extensions.hex
import com.tonapps.blockchain.ton.extensions.toRawAddress
import com.tonapps.blockchain.ton.extensions.toUserFriendly
import com.tonapps.wallet.api.entity.TokenEntity
import com.tonapps.wallet.data.core.entity.RawMessageEntity
import com.tonapps.wallet.data.staking.entities.PoolDetailsEntity
import com.tonapps.wallet.data.staking.entities.PoolEntity
import com.tonapps.wallet.data.staking.entities.PoolInfoEntity
import org.ton.block.AddrStd
import org.ton.block.Coins
import org.ton.cell.Cell
import org.ton.cell.buildCell
import org.ton.tlb.storeTlb
import java.math.BigInteger

object StakingUtils {

    fun getWithdrawalFee(implementation: StakingPool.Implementation): Coins {
        if (implementation == StakingPool.Implementation.Whales) {
            return Coins.of(0.2)
        }
        if (implementation == StakingPool.Implementation.TF || implementation == StakingPool.Implementation.LiquidTF) {
            return Coins.of(1)
        }
        return Coins.of(0)
    }

    fun getWithdrawalAlertFee(
        implementation: StakingPool.Implementation,
        forDisplay: Boolean = false
    ): Coins {
        if (implementation == StakingPool.Implementation.Whales) {
            return Coins.of(0.4)
        }
        if (implementation == StakingPool.Implementation.TF) {
            return Coins.of(1)
        }
        if (implementation == StakingPool.Implementation.LiquidTF) {
            return Coins.of(if (forDisplay) 2 else 1)
        }
        return Coins.of(0)
    }

    fun createWhalesAddStakeCommand(
        queryId: BigInteger
    ): Cell {
        return buildCell {
            storeUInt(3665837821, 32)
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
            storeUInt(0x595f07bc, 32)
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
