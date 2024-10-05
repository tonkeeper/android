package com.tonapps.wallet.api.entity

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import org.json.JSONObject

@Parcelize
data class IAPPackageEntity(
    val id: IAPPackageId,
    val userProceed: Double,
    val productId: String,
) : Parcelable {

    constructor(json: JSONObject) : this(
        id = IAPPackageId.fromId(json.getString("id")),
        userProceed = json.getDouble("user_proceed"),
        productId = json.getString("package_id_android"),
    )
}