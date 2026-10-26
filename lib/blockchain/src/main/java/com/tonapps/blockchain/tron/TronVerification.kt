package com.tonapps.blockchain.tron

internal fun TronTransactionRequest.requireMatches(actual: TronContract) {
    when (this) {
        is TronTransactionRequest.Trx -> {
            val transfer = actual.require<TronContract.Transfer>("TransferContract")
            requireEquals("owner_address", from.tronHex(), transfer.ownerAddress)
            requireEquals("to_address", to.tronHex(), transfer.toAddress)
            requireEquals("amount", amountSun, transfer.amount)
        }

        is TronTransactionRequest.Trc20 -> {
            val call = actual.require<TronContract.SmartContractCall>("TriggerSmartContract")
            requireEquals("owner_address", transfer.from.tronHex(), call.ownerAddress)
            requireEquals("contract_address", transfer.contractAddress.tronHex(), call.contractAddress)
            requireEquals("data", transfer.callData, call.callData)
            requireEquals("call_value", callValue, call.callValue)
            requireEquals("call_token_value", 0, call.callTokenValue)
            requireEquals("token_id", 0, call.tokenId)
        }
    }

    requireAtMost("fee_limit", feeLimit, actual.feeLimit)
}

private inline fun <reified T : TronContract> TronContract.require(expected: String): T {
    return this as? T ?: throw TronTransactionMismatchException(
        field = "contract_type",
        expected = expected,
        actual = typeName,
    )
}

private fun requireEquals(field: String, expected: String, actual: String) {
    if (!expected.equals(actual, ignoreCase = true)) {
        throw TronTransactionMismatchException(field, expected, actual)
    }
}

private fun requireEquals(field: String, expected: Long, actual: Long) {
    if (expected != actual) {
        throw TronTransactionMismatchException(field, expected.toString(), actual.toString())
    }
}

private fun requireAtMost(field: String, limit: Long, actual: Long) {
    if (actual > limit) {
        throw TronTransactionMismatchException(field, "<= $limit", actual.toString())
    }
}
