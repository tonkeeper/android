package com.tonapps.perps.screens.common

import android.content.Context
import com.tonapps.core.helper.navigationDelegate

internal fun Context.openPerpsBasics() {
    navigationDelegate?.onProcessDeeplink("tonkeeper://story/perps")
}
