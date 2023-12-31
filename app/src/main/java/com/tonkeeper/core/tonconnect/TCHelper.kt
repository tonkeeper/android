package com.tonkeeper.core.tonconnect

import com.tonkeeper.core.tonconnect.models.event.TransactionParam
import org.json.JSONArray
import org.json.JSONObject
import org.ton.api.pk.PrivateKeyEd25519
import org.ton.block.AddrStd
import org.ton.block.Message
import org.ton.cell.Cell
import org.ton.contract.wallet.WalletContract
import org.ton.contract.wallet.WalletTransfer
import ton.contract.WalletV4R2Contract
import ton.wallet.Wallet

object TCHelper {

    fun signMessage(
        wallet: Wallet,
        seqno: Int,
        privateKey: PrivateKeyEd25519,
        vararg transfers: WalletTransfer
    ): Message<Cell> {
        return WalletV4R2Contract.createTransferMessage(
            address = AddrStd(wallet.accountId),
            stateInit = if (seqno == 0) wallet.stateInit else null,
            privateKey = privateKey,
            walletId = WalletContract.DEFAULT_WALLET_ID,
            seqno = seqno,
            transfers = transfers
        )
    }

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