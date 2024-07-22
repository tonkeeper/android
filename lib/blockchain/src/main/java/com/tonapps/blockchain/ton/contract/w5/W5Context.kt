package com.tonapps.blockchain.ton.contract.w5

import com.tonapps.blockchain.ton.contract.BaseWalletContract.Companion.DEFAULT_WORKCHAIN
import org.ton.bigint.BigInt
import org.ton.cell.Cell
import org.ton.cell.CellBuilder

sealed class W5Context(
    open val networkGlobalId: Int,
) {

    companion object {

        fun W5Context.getWorkchain(): Int {
            return when (this) {
                is Client -> workchain
                is Custom -> DEFAULT_WORKCHAIN
            }
        }
    }

    val walletId: BigInt
        get() = cell().beginParse().loadInt(32)

    open fun cell(): Cell {
        return Cell()
    }

    data class Client(
        val workchain: Int = DEFAULT_WORKCHAIN,
        val subwalletNumber: Int = 0,
        override val networkGlobalId: Int = -239
    ): W5Context(networkGlobalId) {


        override fun cell() = CellBuilder.createCell {
            storeUInt(1, 1)
            storeUInt(workchain, 8)
            storeUInt(0, 8)
            storeUInt(subwalletNumber, 15)
        }
    }

    data class Custom(
        val id: Int,
        override val networkGlobalId: Int = -239
    ): W5Context(networkGlobalId) {

        override fun cell() = CellBuilder.createCell {
            storeInt(0, 1)
            storeInt(id, 31)
        }
    }

}