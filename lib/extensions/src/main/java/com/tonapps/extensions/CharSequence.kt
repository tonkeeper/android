package com.tonapps.extensions

import android.text.SpannableStringBuilder

val CharSequence.withMinus: CharSequence
    get() {
        if (startsWith("-")) {
            return this
        }

        val builder = SpannableStringBuilder()
        builder.append("− ")
        builder.append(this)
        return builder
    }

val CharSequence.withPlus: CharSequence
    get() {
        if (startsWith("+")) {
            return this
        }

        val builder = SpannableStringBuilder()
        builder.append("+ ")
        builder.append(this)
        return builder
    }

fun CharSequence.plus(text: String): CharSequence {
    val builder = SpannableStringBuilder()
    builder.append(this)
    builder.append(text)
    return builder
}
