package com.tonapps.tonkeeper.core.tonconnect

import com.tonapps.tonkeeper.core.tonconnect.models.event.TransactionParam
import org.json.JSONArray
import org.json.JSONObject
import org.ton.contract.wallet.WalletTransfer

object TCHelper {

    fun createWalletTransfers(array: JSONArray): List<WalletTransfer> {
        val params = parseParams(array)
        if (params.isEmpty()) {
            return emptyList()
        }
        return params.first().createWalletTransfers()
    }

    private fun parseParams(array: JSONArray): List<TransactionParam> {
        val params = mutableListOf<TransactionParam>()
        for (i in 0 until array.length()) {
            val param = parseParam(array[i]) ?: continue
            params.add(param)
        }
        return params
    }

    private fun parseParam(any: Any): TransactionParam? {
        if (any is String) {
            return TransactionParam(any)
        } else if (any is JSONObject) {
            return TransactionParam(any)
        }
        return null
    }
}