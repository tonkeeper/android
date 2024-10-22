package com.tonapps.wallet.data.core.entity

import android.os.Parcelable
import android.util.Log
import com.tonapps.blockchain.ton.extensions.parseCell
import com.tonapps.extensions.optStringCompat
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import org.json.JSONObject
import org.ton.block.AddrStd
import org.ton.block.Coins
import org.ton.block.StateInit
import org.ton.cell.Cell
import org.ton.cell.CellBuilder
import org.ton.cell.buildCell
import org.ton.tlb.CellRef
import org.ton.tlb.asRef

@Parcelize
data class RawMessageEntity(
    val addressValue: String,
    val amount: Long,
    val stateInitValue: String?,
    val payloadValue: String?
): Parcelable {

    @IgnoredOnParcel
    val address: AddrStd by lazy {
        AddrStd.parse(addressValue)
    }

    @IgnoredOnParcel
    val coins: Coins by lazy {
        Coins.ofNano(amount)
    }

    @IgnoredOnParcel
    val stateInit: CellRef<StateInit>? by lazy {
        val cell = stateInitValue?.parseCell() ?: return@lazy null
        cell.asRef(StateInit)
    }

    @IgnoredOnParcel
    val payload: Cell by lazy {
        payloadValue?.parseCell() ?: Cell.empty()
    }

    constructor(json: JSONObject) : this(
        json.getString("address"),
        parseAmount(json.get("amount")),
        json.optStringCompat("stateInit"),
        json.optStringCompat("payload")
    )

    private companion object {

        private fun parseAmount(value: Any): Long {
            if (value is Long) {
                return value
            }
            return value.toString().toLongOrNull() ?: 0
        }
    }

}