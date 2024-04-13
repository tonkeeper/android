package com.tonapps.extensions

import com.tonapps.icu.Punycode

val String.short8: String
    get() {
        if (length < 16) return this
        return substring(0, 8) + "…" + substring(length - 8, length)
    }

val String.short4: String
    get() {
        if (length < 8) return this
        return substring(0, 4) + "…" + substring(length - 4, length)
    }

fun String.ifPunycodeToUnicode(): String {
    return if (startsWith(Punycode.PREFIX_STRING)) {
        Punycode.decodeSafe(this)
    } else {
        this
    }
}

val String.withPlus: String
    get() {
        return if (startsWith("+")) this else "+ $this"
    }

val String.withMinus: String
    get() {
        return if (startsWith("-")) this else "− $this"
    }

val CharSequence.withMinus: CharSequence
    get() {
        return if (startsWith("-")) this else "− $this"
    }

val CharSequence.withPlus: CharSequence
    get() {
        return if (startsWith("+")) this else "+ $this"
    }