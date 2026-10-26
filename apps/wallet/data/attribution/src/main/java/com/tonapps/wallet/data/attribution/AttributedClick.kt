package com.tonapps.wallet.data.attribution

import android.net.Uri
import com.tonapps.wallet.data.attribution.entities.AttributionTouch

private const val APP_SCHEME = "tonkeeper"

internal data class AttributedClick(
    val touch: AttributionTouch,
    val deepLink: Uri?,
)

internal fun String.toAppDeepLink(): Uri? {
    val value = trim().trimStart('/')
    if (value.isEmpty()) {
        return null
    }
    val uri = Uri.parse(value)
    return if (uri.scheme.isNullOrEmpty()) {
        Uri.parse("$APP_SCHEME://$value")
    } else {
        uri
    }
}
