package com.tonkeeper.core.tonconnect.models

import android.net.Uri
import android.os.Parcelable
import com.tonkeeper.api.shortAddress
import kotlinx.parcelize.Parcelize
import ton.extensions.toUserFriendly

@Parcelize
data class TCData(
    val manifest: TCManifest,
    val accountId: String,
    val clientId: String,
    val items: List<TCItem>
): Parcelable {

    val shortAddress: String
        get() = accountId.toUserFriendly().shortAddress

    val url: String
        get() = manifest.url

    val host: String
        get() = Uri.parse(url).host!!
}