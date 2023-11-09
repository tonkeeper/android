package com.tonkeeper.core.tonconnect.models

import android.net.Uri
import com.tonkeeper.api.shortAddress
import com.tonkeeper.api.userLikeAddress

data class TCData(
    val manifest: TCManifest,
    val accountId: String,
    val clientId: String,
    val items: List<TCItem>
) {

    val shortAddress: String
        get() = accountId.userLikeAddress.shortAddress

    val url: String
        get() = manifest.url

    val host: String
        get() = Uri.parse(url).host!!
}