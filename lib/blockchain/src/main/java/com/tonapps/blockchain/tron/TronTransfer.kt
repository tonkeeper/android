package com.tonapps.blockchain.tron

import com.tonapps.chainkit.chain.tron.utils.TronUtils
import java.math.BigInteger
import com.ionspin.kotlin.bignum.integer.BigInteger as KmpBigInteger

data class TronTransfer(
    val from: String,
    val to: String,
    val amount: BigInteger,
    val contractAddress: String,
) {
    val function: String
        get() = "$NAME(address,uint256)"

    val callData: String
        get() {
            require(amount.signum() >= 0) { "TRC20 amount must not be negative" }
            return TronUtils.trc20TransferCallData(to, KmpBigInteger.parseString(amount.toString()))
                ?: throw IllegalArgumentException("Invalid TRC20 transfer")
        }

    val data: String
        get() = callData.substring(8)

    private companion object {

        const val NAME = "transfer"
    }
}
