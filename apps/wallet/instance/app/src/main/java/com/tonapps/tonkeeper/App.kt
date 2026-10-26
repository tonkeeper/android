package com.tonapps.tonkeeper

import android.app.Application
import android.content.res.Configuration
import android.view.View
import androidx.core.text.layoutDirection
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.wallet.data.core.Theme
import com.tonapps.wallet.localization.Localization
import uikit.R
import java.util.Locale
import com.tonapps.chainkit.core.chain.model.num.Formatter as ChainKitFormatter

open class App : Application() {

    fun updateThemes() {
        Theme.clear()
        Theme.add("blue", R.style.Theme_App_Blue, title = getString(Localization.theme_deep_blue))
        Theme.add("dark", R.style.Theme_App_Dark, title = getString(Localization.theme_dark))
        Theme.add(
            "light",
            R.style.Theme_App_Light,
            true,
            title = getString(Localization.theme_light)
        )
        Theme.add("system", 0, title = getString(Localization.system))
    }

    companion object {
        @Deprecated("Initialize object explicitly")
        lateinit var instance: App

        @Deprecated("Migration to Compose")
        fun applyConfiguration(newConfig: Configuration) {
            setFormatterLocale(newConfig.locales[0])
        }

        /**
         * ChainKit reads no locale of its own and its call sites pass no options, so it is handed
         * the ICU decimal separator every time the locale is installed. Grouping stays ChainKit's
         * default space.
         */
        fun setFormatterLocale(locale: Locale) {
            CurrencyFormatter.setLocale(locale)
            ChainKitFormatter.configureDefaults(
                ChainKitFormatter.defaultOptions.copy(
                    decimalSeparator = CurrencyFormatter.decimalSeparator,
                    isolateLtr = locale.layoutDirection == View.LAYOUT_DIRECTION_RTL,
                )
            )
        }
    }
}
