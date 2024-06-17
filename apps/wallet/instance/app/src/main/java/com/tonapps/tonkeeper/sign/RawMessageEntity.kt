package com.tonapps.tonkeeper.sign

import android.os.Parcelable
import android.util.Log
import com.tonapps.blockchain.ton.extensions.parseCell
import com.tonapps.blockchain.ton.extensions.safeParseCell
import com.tonapps.blockchain.ton.extensions.toTlb
import kotlinx.parcelize.Parcelize
import org.json.JSONObject
import org.ton.block.AddrStd
import org.ton.block.Coins
import org.ton.block.StateInit
import org.ton.cell.Cell
import org.ton.contract.wallet.WalletTransfer
import org.ton.contract.wallet.WalletTransferBuilder
import java.math.BigInteger

@Parcelize
data class RawMessageEntity(
    val addressValue: String,
    val amount: Long,
    val stateInitValue: String?,
    val payloadValue: String
): Parcelable {

    val address: AddrStd
        get() = AddrStd.parse(addressValue)

    val coins: Coins
        get() = Coins.ofNano(amount)

    val stateInit: StateInit?
        get() = stateInitValue?.toTlb()

    val payload: Cell
        get() = payloadValue.safeParseCell() ?: Cell()

    val walletTransfer: WalletTransfer by lazy {
        val builder = WalletTransferBuilder()
        builder.stateInit = stateInit
        builder.destination = address
        builder.body = payload
        // builder.bounceable = address.isBounceable()
        builder.coins = coins
        builder.build()
    }

    constructor(json: JSONObject) : this(
        json.getString("address"),
        parseAmount(json.get("amount")),
        json.optString("stateInit"),
        json.optString("payload")
    )

    init {
        Log.d("TonConnectBridge", "raw: $payload")
    }

    private companion object {

        private fun parseAmount(value: Any): Long {
            if (value is Long) {
                return value
            }
            return value.toString().toLong()
        }
    }

}