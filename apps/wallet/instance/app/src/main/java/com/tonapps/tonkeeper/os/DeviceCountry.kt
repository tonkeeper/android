package com.tonapps.tonkeeper.os

import android.content.Context
import android.telephony.TelephonyManager
import java.util.Locale

object DeviceCountry {

    fun fromLocale(): String? {
        val country = Locale.getDefault().country
        if (country.equals("UK", true) || country.equals("GB", true)) {
            return null
        }
        if (country.equals("US", true)) {
            return null
        }
        if (country.equals("ZZ", true)) {
            return null
        }
        return country.uppercase()
    }

    fun fromNetwork(context: Context) = try {
        val manager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        manager.networkCountryIso.ifEmpty { null }
    } catch (ignored: Throwable) {
        null
    }

    fun fromSIM(context: Context): String? {
        return getSimCountry(context) ?: getCountryByMCC(context)
    }

    private fun getSimCountry(context: Context) = try {
        val manager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        manager.simCountryIso.ifEmpty { null }
    } catch (ignored: Throwable) {
        null
    }

    // Hack to get country code from MCC (Mobile Country Code)
    private fun getMCC(context: Context) = try {
        context.resources.configuration.mcc
    } catch (e: Exception) {
        0
    }

    private fun getCountryByMCC(context: Context): String? {
        return when (getMCC(context)) {
            in 310..316 -> "US" // United States
            in 234..235 -> "GB" // United Kingdom
            302 -> "CA" // Canada
            208 -> "FR" // France
            222 -> "GR" // Greece
            230 -> "IT" // Italy
            232 -> "NL" // Netherlands
            238 -> "PT" // Portugal
            240 -> "ES" // Spain
            242 -> "SE" // Sweden
            244 -> "IE" // Ireland
            246 -> "FI" // Finland
            248 -> "DK" // Denmark
            250 -> "NO" // Norway
            255 -> "CH" // Switzerland
            260 -> "BE" // Belgium
            268 -> "AT" // Austria
            270 -> "HU" // Hungary
            276 -> "DE" // Germany
            278 -> "LV" // Latvia
            280 -> "LT" // Lithuania
            282 -> "EE" // Estonia
            283 -> "PL" // Poland
            284 -> "CZ" // Czech Republic
            286 -> "SK" // Slovakia
            288 -> "SI" // Slovenia
            292 -> "RO" // Romania
            293 -> "BG" // Bulgaria
            294 -> "HR" // Croatia
            else -> null
        }
    }
}