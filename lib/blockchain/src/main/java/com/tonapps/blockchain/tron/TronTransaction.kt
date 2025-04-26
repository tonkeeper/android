package com.tonapps.blockchain.tron

import com.google.protobuf.ByteString
import com.tonapps.blockchain.tron.proto.Chain.Transaction
import com.tonapps.blockchain.tron.proto.Contract
import org.json.JSONArray
import org.json.JSONObject
import org.web3j.crypto.ECKeyPair
import org.web3j.crypto.Sign
import java.math.BigInteger
import java.security.MessageDigest

data class TronTransaction(private val tx: Transaction) {
    companion object {
        private fun parseTransactionFromJson(json: JSONObject): Transaction {
            val rawDataHex = json.getString("raw_data_hex")

            val rawBytes = rawDataHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            val rawData = Transaction.raw.parseFrom(rawBytes)

            return Transaction.newBuilder()
                .setRawData(rawData)
                .build()
        }
    }

    constructor(json: JSONObject) : this(
        tx = parseTransactionFromJson(json),
    )

    val txId: String
        get() {
            val rawBytes = tx.rawData.toByteArray()
            val hash = MessageDigest.getInstance("SHA-256").digest(rawBytes)
            return toHex(hash)
        }

    val json: JSONObject
        get() {
            return toJSON()
        }

    private fun toHex(array: ByteArray): String {
        return array.joinToString("") { "%02x".format(it) }
    }

    private fun toJSON(): JSONObject {
        val json = JSONObject()

        val rawData = tx.rawData

        val rawJson = JSONObject().apply {
            put("ref_block_bytes", toHex(rawData.refBlockBytes.toByteArray()))
            put("ref_block_hash", toHex(rawData.refBlockHash.toByteArray()))
            put("expiration", rawData.expiration)
            put("timestamp", rawData.timestamp)
            put("fee_limit", rawData.feeLimit)

            val contracts = JSONArray()
            for (contract in rawData.contractList) {
                val contractJson = JSONObject()
                contractJson.put("type", contract.type.name)

                val parameter = when (contract.type) {
                    Transaction.Contract.ContractType.TriggerSmartContract -> {
                        val unpacked =
                            Contract.TriggerSmartContract.parseFrom(contract.parameter.value)
                        JSONObject().apply {
                            put("owner_address", toHex(unpacked.ownerAddress.toByteArray()))
                            put("contract_address", toHex(unpacked.contractAddress.toByteArray()))
                            put("data", toHex(unpacked.data.toByteArray()))
                        }
                    }

                    else -> {
                        throw IllegalArgumentException("Unsupported contract type: ${contract.type}")
                    }
                }
                contractJson.put("parameter", JSONObject().apply {
                    put("value", parameter)
                    put("type_url", "type.googleapis.com/protocol.TriggerSmartContract")
                })
                contracts.put(contractJson)
            }

            put("contract", contracts)
        }

        json.put("raw_data", rawJson)
        json.put("raw_data_hex", toHex(rawData.toByteArray()))

        if (tx.signatureCount > 0) {
            val signatures = JSONArray()
            for (sig in tx.signatureList) {
                signatures.put(toHex(sig.toByteArray()))
            }
            json.put("signature", signatures)
        }

        json.put("txID", txId)

        json.put("visible", false)

        return json
    }

    fun extendExpiration(extensionSeconds: Long = 600): TronTransaction {
        val currentExpiration = tx.rawData.expiration
        val newExpiration = currentExpiration + (extensionSeconds * 1000)

        val newRaw = tx.rawData.toBuilder()
            .setExpiration(newExpiration)
            .build()

        return TronTransaction(tx.toBuilder().setRawData(newRaw).build())
    }

    fun sign(privateKey: BigInteger): TronTransaction {
        val rawDataBytes = tx.rawData.toByteArray()
        val txHash = MessageDigest.getInstance("SHA-256").digest(rawDataBytes)

        val ecKeyPair = ECKeyPair.create(privateKey)
        val signature = Sign.signMessage(txHash, ecKeyPair, false)

        val signatureBytes = signature.r + signature.s + signature.v

        return TronTransaction(
            tx = tx.toBuilder().addSignature(ByteString.copyFrom(signatureBytes)).build()
        )
    }
}
