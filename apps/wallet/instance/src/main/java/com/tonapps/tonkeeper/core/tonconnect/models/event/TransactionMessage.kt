package com.tonapps.tonkeeper.core.tonconnect.models.event

import android.util.Log
import org.json.JSONObject
import org.ton.block.Coins
import org.ton.block.MsgAddressInt
import org.ton.block.StateInit
import org.ton.boc.BagOfCells
import org.ton.cell.Cell
import org.ton.contract.wallet.WalletTransfer
import org.ton.contract.wallet.WalletTransferBuilder
import org.ton.crypto.base64
import org.ton.tlb.loadTlb
import ton.SendMode
import ton.extensions.cellOf

data class TransactionMessage(
    val address: String,
    val amount: Long,
    val stateInit: String?,
    val payload: String?
) {

    constructor(json: JSONObject) : this(
        json.getString("address"),
        json.getLong("amount"),
        json.optString("stateInit"),
        json.optString("payload")
    )

    fun createWalletTransfer(): WalletTransfer {
        val builder = WalletTransferBuilder()
        builder.destination = MsgAddressInt(address)
        builder.sendMode = SendMode.PAY_GAS_SEPARATELY.value + SendMode.IGNORE_ERRORS.value
        builder.coins = Coins.ofNano(amount)
        builder.body = parsePayload()
        builder.stateInit = parseStateInit()
        return builder.build()
    }

    private fun parseStateInit(): StateInit? {
        if (stateInit.isNullOrBlank()) {
            return null
        }
        return try {
            val cell = cellOf(stateInit)
            cell.parse { loadTlb(StateInit) }
        } catch (e: Throwable) {
            null
        }
    }

    private fun parsePayload(): Cell? {
        if (payload.isNullOrBlank()) {
            return null
        }
        return try {
            cellOf(payload)
        } catch (e: Throwable) {
            null
        }
    }

}
