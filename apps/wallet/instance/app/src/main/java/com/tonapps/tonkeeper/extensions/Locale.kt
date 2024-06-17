package com.tonapps.tonkeeper.extensions

import java.util.Locale

val String.countryEmoji: String
    get() {
        if (this == "NOKYC") {
            return "\uD83C\uDFF4\u200D☠\uFE0F"
        }
        return try {
            val firstLetter = Character.codePointAt(this, 0) - 0x41 + 0x1F1E6
            val secondLetter = Character.codePointAt(this, 1) - 0x41 + 0x1F1E6
            String(Character.toChars(firstLetter)) + String(Character.toChars(secondLetter))
        } catch (e: Exception) {
            this
        }
    }

val String.countryName: String
    get() {
        return try {
            Locale("", this).displayCountry
        } catch (e: Exception) {
            this
        }
    }

val Locale.flagEmoji: String
    get() = country.countryEmoji