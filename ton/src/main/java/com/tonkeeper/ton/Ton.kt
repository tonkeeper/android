package com.tonkeeper.ton

data class Ton(
    val nano: Long
) {

    companion object {
        private const val BASE = 1000000000L

        val ZERO = Ton(0)
    }

    constructor(coins: Float): this((coins * BASE).toLong())

    val coins: Float
        get() = nano / BASE.toFloat()

}