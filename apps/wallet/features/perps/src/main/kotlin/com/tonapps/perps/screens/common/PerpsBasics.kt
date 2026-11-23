package com.tonapps.perps.screens.common

import android.content.Context
import com.tonapps.core.helper.navigationDelegate

const val PERPS_STORY_ID = "perps"

const val PERPS_LIGHTER_TERMS_URL = "https://www.keeperwallet.com/terms/lighter"

internal fun Context.openPerpsBasics() {
    navigationDelegate?.onOpenPerpsBasics()
}
