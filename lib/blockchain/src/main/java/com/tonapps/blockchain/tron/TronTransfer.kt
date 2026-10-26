package com.tonapps.blockchain.tron

import org.web3j.abi.FunctionEncoder
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.generated.Uint256
import java.math.BigInteger

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
            val function = Function(
                NAME,
                listOf(Address(to.toEvmHex()), Uint256(amount)),
                emptyList()
            )

            return FunctionEncoder.encode(function).removePrefix("0x")
        }

    val data: String
        get() = callData.substring(8)

    private companion object {

        const val NAME = "transfer"
    }
}
