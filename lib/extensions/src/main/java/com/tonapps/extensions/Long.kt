package com.tonapps.extensions

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

@OptIn(ExperimentalContracts::class)
fun Long?.isPositive(): Boolean {
    contract {
        returns(true) implies (this@isPositive != null)
    }
    if (this == null) return false
    return this > 0
}
