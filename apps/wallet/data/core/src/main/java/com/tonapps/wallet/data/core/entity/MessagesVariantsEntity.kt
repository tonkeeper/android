package com.tonapps.wallet.data.core.entity

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import org.json.JSONObject

@Parcelize
data class MessagesVariantsEntity(
    val battery: List<RawMessageEntity>
): Parcelable {

    constructor(json: JSONObject) : this(
        battery = RawMessageEntity.parseArray(json.optJSONArray("battery"), true)
    )
}