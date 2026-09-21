package com.tonapps.core.deeplink

import androidx.core.net.toUri

fun isMigrateDeeplink(payload: String): Boolean =
    DeepLinkRoute.resolve(payload.toUri()) is DeepLinkRoute.Migrate
