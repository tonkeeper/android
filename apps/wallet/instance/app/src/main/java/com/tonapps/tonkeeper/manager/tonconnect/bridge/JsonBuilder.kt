package com.tonapps.tonkeeper.manager.tonconnect.bridge

import com.tonapps.blockchain.ton.extensions.base64
import com.tonapps.blockchain.ton.extensions.hex
import com.tonapps.blockchain.ton.proof.TONProof
import com.tonapps.tonkeeper.manager.tonconnect.bridge.model.BridgeError
import com.tonapps.wallet.data.account.entities.WalletEntity
import org.json.JSONArray
import org.json.JSONObject

internal object JsonBuilder {

    fun responseDisconnect(id: Long): JSONObject {
        val json = JSONObject()
        json.put("result", JSONObject())
        json.put("id", id)
        return json
    }

    fun responseSendTransaction(id: Long, boc: String): JSONObject {
        val json = JSONObject()
        json.put("result", boc)
        json.put("id", id)
        return json
    }

    fun responseError(id: Long, error: BridgeError): JSONObject {
        val json = JSONObject()
        json.put("error", error(error))
        json.put("id", id)
        return json
    }

    fun error(error: BridgeError): JSONObject {
        val json = JSONObject()
        json.put("code", error.code)
        json.put("message", error.message)
        return json
    }

    fun connectEventError(error: BridgeError): JSONObject {
        val json = JSONObject()
        json.put("event", "connect_error")
        json.put("id", System.currentTimeMillis())
        json.put("payload", error(error))
        return json
    }

    fun connectEventSuccess(
        wallet: WalletEntity,
        proof: TONProof.Result?,
        proofError: BridgeError?,
        appVersion: String
    ): JSONObject {
        val json = JSONObject()
        json.put("event", "connect")
        json.put("id", System.currentTimeMillis())
        json.put("payload", payload(wallet, proof, proofError, appVersion))
        return json
    }

    fun disconnectEvent(): JSONObject {
        val json = JSONObject()
        json.put("event", "disconnect")
        json.put("id", System.currentTimeMillis())
        json.put("payload", JSONObject())
        return json
    }

    private fun payload(
        wallet: WalletEntity,
        proof: TONProof.Result?,
        proofError: BridgeError?,
        appVersion: String,
    ): JSONObject {
        val json = JSONObject()
        json.put("items", payloadItems(wallet, proof, proofError))
        json.put("device", device(wallet.maxMessages, appVersion))
        return json
    }

    private fun payloadItems(
        wallet: WalletEntity,
        proof: TONProof.Result?,
        proofError: BridgeError?
    ): JSONArray {
        val array = JSONArray()
        array.put(tonAddressItemReply(wallet))
        proof?.let {
            array.put(tonProofItemReplySuccess(it))
        }
        proofError?.let {
            array.put(tonProofItemReplyError(it))
        }
        return array
    }

    private fun tonProofItemReplySuccess(proof: TONProof.Result): JSONObject {
        val json = JSONObject()
        json.put("name", "ton_proof")
        json.put("proof", proof(proof))
        return json
    }

    private fun tonProofItemReplyError(error: BridgeError): JSONObject {
        val json = JSONObject()
        json.put("name", "ton_proof")
        json.put("error", error(error))
        return json
    }

    private fun proof(proof: TONProof.Result): JSONObject {
        val json = JSONObject()
        json.put("timestamp", proof.timestamp)
        json.put("domain", domain(proof.domain.value))
        json.put("signature", proof.signature)
        json.put("payload", proof.payload)
        return json
    }

    private fun domain(value: String): JSONObject {
        val size = value.toByteArray().size
        val json = JSONObject()
        json.put("lengthBytes", size)
        json.put("length_bytes", size)
        json.put("value", value)
        return json
    }

    private fun tonAddressItemReply(wallet: WalletEntity): JSONObject {
        val stateInit = wallet.contract.stateInitCell().base64()
        val json = JSONObject()
        json.put("name", "ton_addr")
        json.put("address", wallet.accountId)
        json.put("network", (if (wallet.testnet) -3 else -239).toString())
        json.put("publicKey", wallet.publicKey.hex())
        json.put("walletStateInit", stateInit)
        return json
    }

    fun device(maxMessages: Int, appVersion: String): JSONObject {
        val json = JSONObject()
        json.put("platform", "android")
        json.put("appName", "Tonkeeper")
        json.put("appVersion", appVersion)
        json.put("maxProtocolVersion", 2)
        json.put("features", features(maxMessages))
        return json
    }

    private fun features(maxMessages: Int): JSONArray {
        val array = JSONArray()
        array.put("SendTransaction")
        array.put(sendTransactionFeature(maxMessages))
        return array
    }

    private fun sendTransactionFeature(maxMessages: Int): JSONObject {
        val json = JSONObject()
        json.put("name", "SendTransaction")
        json.put("maxMessages", maxMessages)
        return json
    }
}