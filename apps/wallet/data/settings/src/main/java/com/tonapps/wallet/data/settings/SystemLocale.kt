package com.tonapps.wallet.data.settings

import android.content.Context
import androidx.core.app.LocaleManagerCompat
import com.tonapps.extensions.locale
import java.util.Locale

/**
 * The device locale, unaffected by a per-app language: on API 33+ a language set through
 * AppCompatDelegate also lands in the application context's configuration.
 */
internal val Context.systemLocale: Locale
    get() = LocaleManagerCompat.getSystemLocales(this).get(0) ?: locale
