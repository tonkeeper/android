package com.tonapps.wallet.localization

import android.util.Log
import java.util.Locale

data class Language(val code: String = DEFAULT) {

    private val langCode: String by lazy {
        code.split("-")[0]
    }

    val locale: Locale by lazy {
        Locale(langCode)
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