package com.tonapps.wallet.data.attribution

import android.net.Uri

data class AttributionDeepLink(
    val appLink: Uri,
    val isColdStart: Boolean,
    val isDeferred: Boolean,
)
