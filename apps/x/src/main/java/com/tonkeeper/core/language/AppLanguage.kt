package com.tonkeeper.core.language

import com.tonapps.tonkeeperx.R
import com.tonkeeper.App
import com.tonkeeper.extensions.capitalized
import java.util.Locale

typealias AppLanguage = String

const val LANGUAGE_DEFAULT: AppLanguage = "default"

val AppLanguage.name: String
    get() {
        if (this == LANGUAGE_DEFAULT) {
            return App.instance.getString(R.string.system)
        }
        return Locale(this).displayName.capitalized
    }