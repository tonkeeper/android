package com.tonkeeper.extensions

import java.util.Locale

val Locale.flagEmoji: String
    get() {
        val firstLetter = Character.codePointAt(country, 0) - 0x41 + 0x1F1E6
        val secondLetter = Character.codePointAt(country, 1) - 0x41 + 0x1F1E6
        return String(Character.toChars(firstLetter)) + String(Character.toChars(secondLetter))
    }