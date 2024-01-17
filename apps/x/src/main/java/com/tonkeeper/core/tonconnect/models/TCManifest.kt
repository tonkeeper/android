package com.tonkeeper.core.tonconnect.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import org.json.JSONObject

@Parcelize
data class TCManifest(
    val url: String,
    val name: String,
    val iconUrl: String,
    val termsOfUseUrl: String?,
    val privacyPolicyUrl: String?
) : Parcelable {

    constructor(json: JSONObject) : this(
        url = json.getString("url"),
        name = json.getString("name"),
        iconUrl = json.getString("iconUrl"),
        termsOfUseUrl = json.optString("termsOfUseUrl"),
        privacyPolicyUrl = json.optString("privacyPolicyUrl")
    )

    constructor(data: String) : this(
        JSONObject(data)
    )
}
