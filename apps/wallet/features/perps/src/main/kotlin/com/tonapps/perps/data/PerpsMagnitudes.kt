package com.tonapps.perps.data

import android.content.Context
import android.content.res.Configuration
import com.tonapps.wallet.localization.Localization
import java.util.Locale

class PerpsMagnitudes(
    val thousands: String,
    val millions: String,
    val billions: String,
    val trillions: String,
) {

    internal fun ofChainKitSuffix(suffix: Char): String? = when (suffix) {
        'M' -> millions
        'B' -> billions
        'T' -> trillions
        else -> null
    }

    companion object {
        val English = PerpsMagnitudes(
            thousands = "%1\$sK",
            millions = "%1\$sM",
            billions = "%1\$sB",
            trillions = "%1\$sT",
        )
    }
}

@Volatile
private var installedMagnitudes = PerpsMagnitudes.English

@Volatile
private var installedMagnitudesLocale: Locale? = null

internal val perpsMagnitudes: PerpsMagnitudes
    get() = installedMagnitudes

fun configurePerpsMagnitudes(context: Context, locale: Locale) {
    if (locale == installedMagnitudesLocale) {
        return
    }
    val configuration = Configuration(context.resources.configuration).apply { setLocale(locale) }
    val resources = context.createConfigurationContext(configuration).resources
    configurePerpsMagnitudes(
        PerpsMagnitudes(
            thousands = resources.getString(Localization.perps_number_thousands),
            millions = resources.getString(Localization.perps_number_millions),
            billions = resources.getString(Localization.perps_number_billions),
            trillions = resources.getString(Localization.perps_number_trillions),
        )
    )
    installedMagnitudesLocale = locale
}

internal fun configurePerpsMagnitudes(magnitudes: PerpsMagnitudes) {
    installedMagnitudes = magnitudes
}
