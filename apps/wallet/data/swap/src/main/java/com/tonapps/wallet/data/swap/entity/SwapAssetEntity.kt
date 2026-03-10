package com.tonapps.wallet.data.swap.entity

import android.os.Parcelable
import com.tonapps.wallet.data.core.currency.WalletCurrency
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import org.json.JSONObject

@Parcelize
data class SwapAssetEntity(
    val address: String,
    val decimals: Int,
    val image: String,
    val name: String,
    val symbol: String
): Parcelable {

    @IgnoredOnParcel
    val currency: WalletCurrency by lazy {
        if (address.equals("ton", true)) {
            WalletCurrency.TON
        } else if (address == WalletCurrency.USDT_TON_ADDRESS) {
            WalletCurrency.USDT_TON
        } else {
            WalletCurrency(
                code = symbol,
                title = name,
                chain = WalletCurrency.Chain.TON(address, decimals),
                iconUrl = image,
            )
        }
    }

    constructor(json: JSONObject) : this(
        address = json.getString("address"),
        decimals = json.getInt("decimals"),
        image = json.getString("image"),
        name = json.getString("name"),
        symbol = json.getString("symbol")
    )
}