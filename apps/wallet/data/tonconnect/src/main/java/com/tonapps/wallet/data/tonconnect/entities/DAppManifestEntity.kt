package com.tonapps.wallet.data.tonconnect.entities

import android.net.Uri
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import org.json.JSONObject

@Parcelize
class DAppManifestEntity(
    val url: String,
    val name: String,
    val iconUrl: String,
    val termsOfUseUrl: String?,
    val privacyPolicyUrl: String?
) : Parcelable {

    val host: String
        get() = Uri.parse(url).host ?: name

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