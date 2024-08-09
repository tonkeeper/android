package com.tonapps.wallet.data.tonconnect.entities

import android.net.Uri
import android.os.Parcelable
import com.tonapps.security.hex
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import org.json.JSONObject
import org.ton.crypto.digest.sha256

@Parcelize
data class DAppManifestEntity(
    val url: String,
    val name: String,
    val iconUrl: String,
    val termsOfUseUrl: String?,
    val privacyPolicyUrl: String?,
    val source: String,
) : Parcelable {

    val host: String
        get() = Uri.parse(url).host ?: name

    @IgnoredOnParcel
    val urlHash: String by lazy {
        hex(sha256(url.toByteArray()))
    }

    constructor(json: JSONObject, source: String) : this(
        url = json.getString("url").removeSuffix("/"),
        name = json.getString("name"),
        iconUrl = json.getString("iconUrl"),
        termsOfUseUrl = json.optString("termsOfUseUrl"),
        privacyPolicyUrl = json.optString("privacyPolicyUrl"),
        source = source
    )
}