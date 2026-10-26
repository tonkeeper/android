package com.tonapps.blockchain.tron

import com.google.protobuf.ByteString
import com.tonapps.blockchain.tron.proto.Chain.Transaction
import com.tonapps.blockchain.tron.proto.Contract

sealed interface TronContract {

    val typeName: String
    val typeUrl: String
    val ownerAddress: String
    val feeLimit: Long

    data class SmartContractCall(
        override val ownerAddress: String,
        override val feeLimit: Long,
        val contractAddress: String,
        val callData: String,
        val callValue: Long,
        val callTokenValue: Long,
        val tokenId: Long,
    ) : TronContract {

        override val typeName: String =
            Transaction.Contract.ContractType.TriggerSmartContract.name

        override val typeUrl: String = "type.googleapis.com/protocol.TriggerSmartContract"
    }

    data class Transfer(
        override val ownerAddress: String,
        override val feeLimit: Long,
        val toAddress: String,
        val amount: Long,
    ) : TronContract {

        override val typeName: String =
            Transaction.Contract.ContractType.TransferContract.name

        override val typeUrl: String = "type.googleapis.com/protocol.TransferContract"
    }
}

internal fun ByteArray.toTronHex(): String = joinToString("") { "%02x".format(it) }

internal fun ByteString.toTronHex(): String = toByteArray().toTronHex()

internal fun Transaction.raw.toTronContract(): TronContract {
    if (contractCount != 1) {
        throw TronTransactionMismatchException(
            field = "contract_count",
            expected = "1",
            actual = contractCount.toString(),
        )
    }

    requireNoUncomparedFields(this)

    val contract = contractList.first()
    requireNoUncomparedFields(contract)

    return when (contract.type) {
        Transaction.Contract.ContractType.TriggerSmartContract -> {
            val unpacked = Contract.TriggerSmartContract.parseFrom(contract.parameter.value)
            TronContract.SmartContractCall(
                ownerAddress = unpacked.ownerAddress.toTronHex(),
                feeLimit = feeLimit,
                contractAddress = unpacked.contractAddress.toTronHex(),
                callData = unpacked.data.toTronHex(),
                callValue = unpacked.callValue,
                callTokenValue = unpacked.callTokenValue,
                tokenId = unpacked.tokenId,
            )
        }

        Transaction.Contract.ContractType.TransferContract -> {
            val unpacked = Contract.TransferContract.parseFrom(contract.parameter.value)
            TronContract.Transfer(
                ownerAddress = unpacked.ownerAddress.toTronHex(),
                feeLimit = feeLimit,
                toAddress = unpacked.toAddress.toTronHex(),
                amount = unpacked.amount,
            )
        }

        else -> throw IllegalArgumentException("Unsupported contract type: ${contract.type}")
    }
}

private fun requireNoUncomparedFields(contract: Transaction.Contract) {
    if (!contract.provider.isEmpty) {
        throw TronTransactionMismatchException("provider", "", contract.provider.toTronHex())
    }

    if (!contract.contractName.isEmpty) {
        throw TronTransactionMismatchException("contract_name", "", contract.contractName.toTronHex())
    }

    if (contract.permissionId != 0) {
        throw TronTransactionMismatchException("permission_id", "0", contract.permissionId.toString())
    }
}

private fun requireNoUncomparedFields(raw: Transaction.raw) {
    if (!raw.data.isEmpty) {
        throw TronTransactionMismatchException("data", "", raw.data.toTronHex())
    }

    if (raw.authsCount != 0) {
        throw TronTransactionMismatchException("auths", "0", raw.authsCount.toString())
    }

    if (!raw.scripts.isEmpty) {
        throw TronTransactionMismatchException("scripts", "", raw.scripts.toTronHex())
    }
}
