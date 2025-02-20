package com.tonapps.wallet.data.core.entity

import android.os.Parcelable
import android.util.Log
import com.tonapps.blockchain.ton.extensions.cellFromBase64
import com.tonapps.blockchain.ton.extensions.isValidTonAddress
import com.tonapps.extensions.optStringCompat
import com.tonapps.extensions.optStringCompatJS
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import org.json.JSONArray
import org.json.JSONObject
import org.ton.block.AddrStd
import org.ton.block.Coins
import org.ton.block.StateInit
import org.ton.cell.Cell
import org.ton.tlb.CellRef
import org.ton.tlb.asRef

@Parcelize
data class RawMessageEntity(
    val addressValue: String,
    val amount: Long,
    val stateInitValue: String?,
    val payloadValue: String?,
    val withBattery: Boolean = false
): Parcelable {

    @IgnoredOnParcel
    val address: AddrStd by lazy {
        AddrStd.parse(addressValue)
    }

    @IgnoredOnParcel
    val coins: Coins by lazy {
        Coins.ofNano(amount)
    }

    constructor(json: JSONObject, withBattery: Boolean) : this(
        addressValue = json.getString("address"),
        amount = parseAmount(json.get("amount")),
        stateInitValue = json.optStringCompatJS("stateInit"),
        payloadValue = json.optStringCompatJS("payload"),
        withBattery = withBattery,
    ) {
        if (stateInitValue?.startsWith("{") == true) { // for dudes how try to send JS Buffer
            throw IllegalArgumentException("Invalid data format. Base64 encoding required for data transfer, JavaScript objects not supported. Received: stateInit =  $stateInitValue")
        }
        if (payloadValue?.startsWith("{") == true) { // for dudes how try to send JS Buffer
            throw IllegalArgumentException("Invalid data format. Base64 encoding required for data transfer, JavaScript objects not supported. Received: payload = $payloadValue")
        }
    }

    fun getStateInitRef(): CellRef<StateInit>? {
        try {
            val cell = stateInitValue?.cellFromBase64() ?: return null
            return cell.asRef(StateInit)
        } catch (e: Throwable) {
            throw IllegalArgumentException("Invalid data format. Received: stateInit = $stateInitValue", e)
        }
    }

    fun getPayload(): Cell {
        try {
            return payloadValue?.cellFromBase64() ?: Cell.empty()
        } catch (e: Throwable) {
            throw IllegalArgumentException("Invalid data format. Received: payload = $payloadValue", e)
        }
    }

    companion object {

        private fun parseAmount(value: Any): Long {
            if (value is Long) {
                return value
            }
            return value.toString().toLong()
        }

        fun parseArray(array: JSONArray?, withBattery: Boolean): List<RawMessageEntity> {
            val messages = mutableListOf<RawMessageEntity>()
            if (array == null || array.length() == 0) {
                return messages
            }
            for (i in 0 until array.length()) {
                val json = array.getJSONObject(i)
                val raw = RawMessageEntity(json, withBattery)
                if (0 >= raw.amount) {
                    throw IllegalArgumentException("Invalid amount: ${raw.amount}")
                }
                if (!raw.addressValue.isValidTonAddress()) {
                    throw IllegalArgumentException("Invalid address: ${raw.addressValue}")
                }
                messages.add(raw)
            }
            return messages
        }
    }

}