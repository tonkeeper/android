package com.tonapps.extensions

import java.util.Locale

fun String.titlecase() = replaceFirstChar(Char::titlecase)

fun String.titlecase(locale: Locale) = replaceFirstChar { it.titlecase(locale) }
