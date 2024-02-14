package com.tonapps.tonkeeper.core.language

import com.tonapps.wallet.localization.Localization
import com.tonapps.tonkeeper.extensions.capitalized
import java.util.Locale

typealias AppLanguage = String

const val LANGUAGE_DEFAULT: AppLanguage = "default"

val AppLanguage.name: String
    get() {
        if (this == LANGUAGE_DEFAULT) {
            return com.tonapps.tonkeeper.App.instance.getString(Localization.system)
        }
        return Locale(this).displayName.capitalized
    }