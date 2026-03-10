package com.tonapps.wallet.api

import android.net.Uri

data class SwapAssetParam(
    val address: String,
    val amount: String?,
) {

    val isEmpty: Boolean
        get() = amount.isNullOrBlank() || amount == "0"

    fun apply(prefix: String, builder: Uri.Builder): Uri.Builder {
        if (address.equals("ton", true)) {
            builder.appendQueryParameter("${prefix}Asset", "0:0000000000000000000000000000000000000000000000000000000000000000")
        } else {
            builder.appendQueryParameter("${prefix}Asset", address)
        }
        amount?.let {
            builder.appendQueryParameter("${prefix}Amount", it)
        }
        return builder
    }
}