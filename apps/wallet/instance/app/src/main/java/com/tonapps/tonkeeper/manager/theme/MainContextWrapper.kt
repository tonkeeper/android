package com.tonapps.tonkeeper.manager.theme

import android.content.Context
import android.content.ContextWrapper
import com.tonapps.log.L

class MainContextWrapper(base: Context): ContextWrapper(base) {

    init {
        L.d("RootActivityLog", "ThemeContextWrapper init")
    }
}