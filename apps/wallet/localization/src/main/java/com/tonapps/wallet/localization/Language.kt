package com.tonapps.wallet.localization

import java.util.Locale

data class Language(val code: String = DEFAULT) {

    val locale: Locale by lazy {
        Locale(code)
    }

    val name: String by lazy {
        if (code == DEFAULT) {
            DEFAULT
        } else {
            locale.displayName
        }
    }

    val nameLocalized: String by lazy {
        if (code == DEFAULT) {
            ""
        } else {
            locale.getDisplayLanguage(locale)
        }
    }

    companion object {
        const val DEFAULT = "default"
    }
}