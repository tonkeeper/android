package com.tonkeeper.extensions

private const val BASE = 1000000000L

fun Long.toCoin() = this / BASE.toFloat()
