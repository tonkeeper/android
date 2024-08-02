package com.tonapps.extensions

val CharSequence.withMinus: CharSequence
    get() = if (startsWith("-")) this else "− $this"

val CharSequence.withPlus: CharSequence
    get() = if (startsWith("+")) this else "+ $this"