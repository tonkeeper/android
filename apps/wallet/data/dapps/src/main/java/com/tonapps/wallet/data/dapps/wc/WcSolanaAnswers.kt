package com.tonapps.wallet.data.dapps.wc

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The JSON of WalletConnect's Solana methods
 * (https://docs.reown.com/advanced/multichain/rpc-reference/solana-rpc), so the confirm screen
 * deals in plain strings. chainkit signs a transaction into `{"signature": <base58>,
 * "transaction": <base64>}`, which is the solana_signTransaction answer as is; the other answers are
 * built from it here.
 */
object WcSolanaAnswers {

    private const val SIGNATURE = "signature"
    private const val TRANSACTION = "transaction"
    private const val TRANSACTIONS = "transactions"

    /** solana_signAllTransactions request: its transactions, packed into one confirm message. */
    fun packTransactions(transactions: List<String>): String {
        return JsonArray(transactions.map(::JsonPrimitive)).toString()
    }

    fun unpackTransactions(packed: String): List<String> {
        return Json.parseToJsonElement(packed).jsonArray.map { it.jsonPrimitive.content }
    }

    /** The base64 transaction of a signed one, to broadcast it. */
    fun signedTransaction(signed: String): String {
        return Json.parseToJsonElement(signed).jsonObject.getValue(TRANSACTION).jsonPrimitive.content
    }

    /** solana_signAndSendTransaction answer, once [signed] is broadcast: `{"signature": <base58>}`. */
    fun sent(signed: String): String {
        val signature = Json.parseToJsonElement(signed).jsonObject.getValue(SIGNATURE)
        return buildJsonObject { put(SIGNATURE, signature) }.toString()
    }

    /** solana_signAllTransactions answer: `{"transactions": [<base64>, ...]}`, in the request's order. */
    fun signedAll(signed: List<String>): String {
        val transactions = JsonArray(signed.map { JsonPrimitive(signedTransaction(it)) })
        return buildJsonObject { put(TRANSACTIONS, transactions) }.toString()
    }
}
