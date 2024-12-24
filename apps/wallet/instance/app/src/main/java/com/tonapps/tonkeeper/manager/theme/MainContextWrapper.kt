package com.tonapps.tonkeeper.manager.theme

import android.content.Context
import android.content.ContextWrapper
import android.util.Log

class MainContextWrapper(base: Context): ContextWrapper(base) {

    init {
        Log.d("RootActivityLog", "ThemeContextWrapper init")
    }
}