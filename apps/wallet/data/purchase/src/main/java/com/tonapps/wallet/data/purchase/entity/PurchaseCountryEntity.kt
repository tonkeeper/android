package com.tonapps.wallet.data.purchase.entity

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class PurchaseCountryEntity(
    val countryCode: String,
    val currency: String,
    val methods: List<String>
): Parcelable