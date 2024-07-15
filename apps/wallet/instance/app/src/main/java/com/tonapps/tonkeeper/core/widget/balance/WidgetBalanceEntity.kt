package com.tonapps.tonkeeper.core.widget.balance

import android.os.Parcelable
import com.tonapps.tonkeeper.api.shortAddress
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
data class WidgetBalanceEntity(
    val tonBalance: CharSequence,
    val currencyBalance: CharSequence,
    val walletAddress: String,
    val label: CharSequence?
): Parcelable {

    @IgnoredOnParcel
    val shortAddress = walletAddress.shortAddress


    val name: String
        get() = label?.toString() ?: shortAddress
}