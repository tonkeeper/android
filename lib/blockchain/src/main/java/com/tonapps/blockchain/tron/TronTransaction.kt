package com.tonapps.blockchain.tron

import com.google.protobuf.ByteString
import com.tonapps.blockchain.tron.proto.Chain.Transaction
import org.json.JSONArray
import org.json.JSONObject
import org.web3j.crypto.ECKeyPair
import org.web3j.crypto.Sign
import java.math.BigInteger
import java.security.MessageDigest

class TronTransaction private constructor(private val tx: Transaction) {

    val contract: TronContract
        get() = tx.rawData.toTronContract()

    val txId: String
        get() {
            val rawBytes = tx.rawData.toByteArray()
            val hash = MessageDigest.getInstance("SHA-256").digest(rawBytes)
            return hash.toTronHex()
        }

    val json: JSONObject
        get() {
            return toJSON()
        }

    val rawDataHex: String
        get() {
            return tx.rawData.toByteArray().toTronHex()
        }

    private fun toJSON(): JSONObject {
        val json = JSONObject()

        val rawData = tx.rawData
        val contract = this.contract

        val rawJson = JSONObject().apply {
            put("ref_block_bytes", rawData.refBlockBytes.toTronHex())
            put("ref_block_hash", rawData.refBlockHash.toTronHex())
            put("expiration", rawData.expiration)
            put("timestamp", rawData.timestamp)
            if (rawData.feeLimit > 0) {
                put("fee_limit", rawData.feeLimit)
            }

            val parameter = when (contract) {
                is TronContract.SmartContractCall -> JSONObject().apply {
                    put("owner_address", contract.ownerAddress)
                    put("contract_address", contract.contractAddress)
                    put("data", contract.callData)
                }

                is TronContract.Transfer -> JSONObject().apply {
                    put("owner_address", contract.ownerAddress)
                    put("to_address", contract.toAddress)
                    put("amount", contract.amount)
                }
            }

            val contractJson = JSONObject().apply {
                put("type", contract.typeName)
                put("parameter", JSONObject().apply {
                    put("value", parameter)
                    put("type_url", contract.typeUrl)
                })
            }

            put("contract", JSONArray().put(contractJson))
        }

        json.put("raw_data", rawJson)
        json.put("raw_data_hex", rawData.toByteArray().toTronHex())

        if (tx.signatureCount > 0) {
            val signatures = JSONArray()
            for (sig in tx.signatureList) {
                signatures.put(sig.toTronHex())
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

    companion object {

        fun fromNodeResponse(json: JSONObject, request: TronTransactionRequest): TronTransaction {
            return fromRawDataHex(json.getString("raw_data_hex"), request)
        }

        fun fromRawDataHex(rawDataHex: String, request: TronTransactionRequest): TronTransaction {
            val rawBytes = rawDataHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            val rawData = Transaction.raw.parseFrom(rawBytes)

            val transaction = TronTransaction(
                tx = Transaction.newBuilder()
                    .setRawData(rawData)
                    .build()
            )

            request.requireMatches(transaction.contract)

            return transaction
        }
    }
}
