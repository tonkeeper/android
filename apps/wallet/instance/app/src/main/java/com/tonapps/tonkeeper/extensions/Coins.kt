package com.tonapps.tonkeeper.extensions

import com.tonapps.icu.Coins

fun Coins.toGrams() = org.ton.block.Coins.ofNano(toLong())