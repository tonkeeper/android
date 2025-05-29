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
    val function: String,
    val contractAddress: String
) {
    val data: String
        get() {
            val function = Function(
                "transfer",
                listOf(Address(to.toEvmHex()), Uint256(amount)),
                emptyList()
            )

            val encoded = FunctionEncoder.encode(function)
            return encoded.removePrefix("0x").substring(8)
        }


    constructor(from: String, to: String, amount: BigInteger, contractAddress: String) : this(
        from = from,
        to = to,
        amount = amount,
        function = "transfer(address,uint256)",
        contractAddress = contractAddress
    )
}
