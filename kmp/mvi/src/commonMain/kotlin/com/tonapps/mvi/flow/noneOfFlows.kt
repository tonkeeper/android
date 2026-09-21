package com.tonapps.mvi.flow

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

fun noneOfFlows(
    vararg flows: Flow<Boolean>
): Flow<Boolean> {
    return combine(flows = flows) { values ->
        values.none { it }
    }
}

fun anyOfFlows(
    vararg flows: Flow<Boolean>
): Flow<Boolean> {
    return combine(flows = flows) { values ->
        values.any { it }
    }
}
