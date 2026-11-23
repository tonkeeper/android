package com.tonapps.deposit.multicoin.screens.confirm.perpetual

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.tonapps.wallet.localization.Localization
import kotlin.math.roundToInt

@Composable
internal fun arrivalTimeValue(seconds: Long): String {
    return stringResource(Localization.perps_withdraw_arrival_value, arrivalMinutes(seconds))
}

fun approximateArrivalMinutes(seconds: Long): Int {
    return (seconds.toDouble() / SECONDS_IN_MINUTE).roundToInt().coerceAtLeast(1)
}

internal fun arrivalMinutes(seconds: Long): Long {
    return ((seconds + SECONDS_IN_MINUTE - 1) / SECONDS_IN_MINUTE).coerceAtLeast(1L)
}

private const val SECONDS_IN_MINUTE = 60L
