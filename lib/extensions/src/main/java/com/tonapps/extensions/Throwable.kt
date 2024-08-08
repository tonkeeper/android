package com.tonapps.extensions

val Throwable.bestMessage: String
    get() = localizedMessage ?: message ?: toString()