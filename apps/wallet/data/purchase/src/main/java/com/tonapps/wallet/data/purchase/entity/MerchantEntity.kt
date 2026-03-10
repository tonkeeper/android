package com.tonapps.wallet.data.purchase.entity

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Parcelize
@Serializable
data class MerchantEntity(
    val id: String,
    val title: String,
    val description: String,
    val image: String,
    val fee: Double,
    val buttons: List<Button>
): Parcelable {

    @Parcelize
    @Serializable
    data class Button(
        val title: String,
        val url: String
    ): Parcelable
}
